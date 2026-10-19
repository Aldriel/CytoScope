package com.bioinfo.cytoscope.server.billing;

import com.bioinfo.cytoscope.server.HttpError;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory ledger of access codes and credit balances. Thread-safe for concurrent uploads.
 */
public final class CreditBook {

    private static final char[] CODE_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 10;

    private final ConcurrentHashMap<String, AccessAccount> accounts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Looks up an account by its access code.
     *
     * @param accessCode the code
     * @return the account, empty when unknown
     */
    public Optional<AccessAccount> find(String accessCode) {
        if (accessCode == null || accessCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(accounts.get(normalize(accessCode)));
    }

    /**
     * Creates a pending account for a Checkout session (zero credits until Stripe confirms).
     *
     * @param email buyer email; may be blank
     * @return the new account
     */
    public AccessAccount createPending(String email) {
        String code = newCode();
        AccessAccount account = new AccessAccount(code, email, 0, 0, "", "", "", "", true);
        accounts.put(code, account);
        return account;
    }

    /**
     * Refills an account after a successful subscription payment (or creates it).
     *
     * @param accessCode existing code, or null to mint a new one
     * @param email buyer email
     * @param allowance monthly credits of the price
     * @param plan plan id from Stripe metadata
     * @param level level id from Stripe metadata
     * @param customerId Stripe customer id
     * @param subscriptionId Stripe subscription id
     * @return the granted account, with balance set to the allowance
     */
    public AccessAccount grant(String accessCode, String email, int allowance, String plan,
                               String level, String customerId, String subscriptionId) {
        if (allowance < 0) {
            throw new IllegalArgumentException("allowance must be >= 0");
        }
        String code = accessCode == null || accessCode.isBlank() ? newCode()
                : normalize(accessCode);
        AccessAccount granted = new AccessAccount(code, email, allowance, allowance, plan, level,
                customerId, subscriptionId, true);
        accounts.put(code, granted);
        return granted;
    }

    /**
     * Finds an account by its Stripe subscription id.
     *
     * @param subscriptionId Stripe subscription id
     * @return the account, empty when unknown
     */
    public Optional<AccessAccount> findBySubscription(String subscriptionId) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return Optional.empty();
        }
        return accounts.values().stream()
                .filter(a -> subscriptionId.equals(a.stripeSubscriptionId()))
                .findFirst();
    }

    /**
     * Marks the subscription inactive when Stripe reports cancellation.
     *
     * @param subscriptionId Stripe subscription id
     * @return the updated account, empty when unknown
     */
    public Optional<AccessAccount> deactivateSubscription(String subscriptionId) {
        Optional<AccessAccount> found = findBySubscription(subscriptionId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AccessAccount updated = found.get().deactivated();
        accounts.put(updated.accessCode(), updated);
        return Optional.of(updated);
    }

    /**
     * Spends credits for an analysis. Fails when the code is unknown, inactive or short of
     * credits.
     *
     * @param accessCode the buyer code
     * @param credits credits to spend; 0 is a no-op
     * @return the account after the debit
     * @throws HttpError 402 when payment or more credits are required
     * @throws HttpError 404 when the code is unknown
     */
    public AccessAccount debit(String accessCode, int credits) {
        if (credits < 0) {
            throw new IllegalArgumentException("credits must be >= 0");
        }
        String code = normalize(accessCode);
        while (true) {
            AccessAccount current = accounts.get(code);
            if (current == null) {
                throw new HttpError(404, "Unknown access code");
            }
            if (!current.active()) {
                throw new HttpError(402, "Subscription inactive; renew to analyse more files");
            }
            if (credits == 0) {
                return current;
            }
            if (current.credits() < credits) {
                throw new HttpError(402, "Not enough credits: need " + credits + ", have "
                        + current.credits());
            }
            AccessAccount updated = current.withCredits(current.credits() - credits);
            if (accounts.replace(code, current, updated)) {
                return updated;
            }
        }
    }

    /**
     * Returns how many accounts are stored (for health / tests).
     *
     * @return the count
     */
    public int size() {
        return accounts.size();
    }

    private String newCode() {
        for (int attempt = 0; attempt < 32; attempt++) {
            char[] chars = new char[CODE_LENGTH];
            for (int i = 0; i < chars.length; i++) {
                chars[i] = CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)];
            }
            String code = new String(chars);
            if (!accounts.containsKey(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not mint an access code");
    }

    private static String normalize(String accessCode) {
        return accessCode.strip().toUpperCase(Locale.ROOT);
    }
}
