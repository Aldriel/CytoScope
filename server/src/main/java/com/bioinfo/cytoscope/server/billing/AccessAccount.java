package com.bioinfo.cytoscope.server.billing;

import java.util.Objects;

/**
 * A paid CytoScope access: the code the buyer types, the monthly credit allowance and the current
 * balance.
 *
 * @param accessCode secret code sent to the buyer
 * @param email buyer email, may be blank before Checkout fills it
 * @param credits remaining credits for the current period
 * @param allowance monthly credits of the subscribed level
 * @param plan {@code individual}, {@code group} or {@code unlimited}; blank when unknown
 * @param level {@code basic}, {@code premium} or {@code ultimate}; blank when unknown
 * @param stripeCustomerId Stripe customer id, blank when unset
 * @param stripeSubscriptionId Stripe subscription id, blank when unset
 * @param active false when the subscription was cancelled and the balance should not refill
 */
public record AccessAccount(
        String accessCode,
        String email,
        int credits,
        int allowance,
        String plan,
        String level,
        String stripeCustomerId,
        String stripeSubscriptionId,
        boolean active) {

    /**
     * Copies the strings and rejects a blank access code.
     */
    public AccessAccount {
        accessCode = requireCode(accessCode);
        email = email == null ? "" : email.strip();
        plan = plan == null ? "" : plan.strip();
        level = level == null ? "" : level.strip();
        stripeCustomerId = stripeCustomerId == null ? "" : stripeCustomerId.strip();
        stripeSubscriptionId = stripeSubscriptionId == null ? "" : stripeSubscriptionId.strip();
        if (credits < 0 || allowance < 0) {
            throw new IllegalArgumentException("credits and allowance must be >= 0");
        }
    }

    private static String requireCode(String accessCode) {
        String code = Objects.requireNonNull(accessCode, "accessCode").strip();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("accessCode is blank");
        }
        return code;
    }

    /**
     * Returns a copy with another balance.
     *
     * @param newCredits remaining credits
     * @return the updated account
     */
    public AccessAccount withCredits(int newCredits) {
        return new AccessAccount(accessCode, email, newCredits, allowance, plan, level,
                stripeCustomerId, stripeSubscriptionId, active);
    }

    /**
     * Returns a copy marked inactive (subscription ended).
     *
     * @return the updated account
     */
    public AccessAccount deactivated() {
        return new AccessAccount(accessCode, email, credits, allowance, plan, level,
                stripeCustomerId, stripeSubscriptionId, false);
    }
}
