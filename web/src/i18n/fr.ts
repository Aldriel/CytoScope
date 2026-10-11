/**
 * Textes de l’interface en français, avec le vocabulaire du rapport PDF (report_fr.properties).
 * Ce catalogue définit les clés : le catalogue anglais doit avoir exactement la même forme.
 * Utiliser l’apostrophe typographique (’).
 */
import type {
  AnalysisIssue,
  CellCyclePhase,
  CellFate,
  CompensationSource,
  Quadrant,
  SampleRole,
  ScaleType,
} from '../model/types';

/** Écrit un nombre à la française (2,1), avec un nombre fixe de décimales. */
const decimals = (value: number, digits: number) =>
  value.toLocaleString('fr-CA', { minimumFractionDigits: digits, maximumFractionDigits: digits });

/** Accorde un nom avec un nombre : « 1 tube », « 3 tubes ». */
const plural = (count: number, one: string, many: string) =>
  `${count.toLocaleString('fr-CA')} ${count <= 1 ? one : many}`;

const role: Record<SampleRole, string> = {
  SAMPLE: 'Échantillon',
  UNSTAINED: 'Témoin non marqué',
  SINGLE_STAIN: 'Simple marquage',
};

const source: Record<CompensationSource, string> = {
  NONE: 'Aucune',
  FILE: 'Matrice des fichiers',
  CONTROLS: 'Calculée à partir des simples marquages',
  EDITED: 'Modifiée à la main',
};

const scale: Record<ScaleType, string> = {
  LINEAR: 'Linéaire',
  LOG: 'Logarithmique',
  LOGICLE: 'Logicle',
  ARCSINH: 'Arcsinh',
};

const fate: Record<CellFate, string> = {
  LIVE: 'Vivantes',
  EARLY_APOPTOTIC: 'Apoptose précoce',
  LATE_APOPTOTIC: 'Apoptose tardive',
  NECROTIC: 'Nécrotiques',
};

const phase: Record<CellCyclePhase, string> = {
  SUB_G1: 'Sub-G1',
  G0_G1: 'G0/G1',
  S: 'S',
  G2_M: 'G2/M',
  ABOVE_G2: '> G2/M',
};

const quadrantSigns: Record<Quadrant, [string, string]> = {
  NEGATIVE_NEGATIVE: ['−', '−'],
  POSITIVE_NEGATIVE: ['+', '−'],
  POSITIVE_POSITIVE: ['+', '+'],
  NEGATIVE_POSITIVE: ['−', '+'],
};

const issue: Record<AnalysisIssue, string> = {
  NO_METRIC: 'aucune valeur à comparer n’est choisie.',
  NO_CONTROL_GROUP: 'aucun groupe témoin n’est choisi, ou il n’a pas de tube.',
  TOO_FEW_GROUPS: 'il faut au moins deux groupes.',
  TOO_FEW_REPLICATES: 'trop peu de réplicats pour estimer la variance.',
  METRIC_UNAVAILABLE: 'la valeur choisie n’est pas disponible pour ces tubes.',
  TOO_FEW_DOSES: 'il faut au moins cinq tubes et deux doses non nulles.',
  NO_G1_PEAK: 'aucun pic G0/G1 trouvé.',
};

export const fr = {
  locale: 'fr',
  languageName: 'Français',

  menu: {
    file: 'Fichier',
    newExperiment: 'Nouvelle expérience',
    open: 'Ouvrir des fichiers FCS…',
    examples: 'Expériences d’exemple',
    report: 'Télécharger le rapport',
    options: 'Options',
    language: 'Langue',
    textSize: 'Taille du texte',
    textSizes: ['Petite', 'Normale', 'Grande', 'Très grande'],
    zoomIn: 'Agrandir le texte',
    zoomOut: 'Réduire le texte',
    zoomReset: 'Taille de texte par défaut',
    help: 'Aide',
    guide: 'Guide d’utilisation',
    shortcuts: 'Raccourcis clavier',
    fcsStandard: 'Norme FCS 3.1 (ISAC)',
    dataset: 'Données U-87MG (Mendeley Data)',
    about: 'À propos de CytoScope',
  },

  tab: {
    tubes: 'Tubes',
    compensation: 'Compensation',
    gating: 'Fenêtrage',
    analysis: 'Analyses',
    results: 'Résultats',
    report: 'Rapport',
  },

  example: {
    cadmium: 'Cadmium sur splénocytes, série de doses (simulée)',
    'u87-apoptosis': 'U-87MG, annexine V / IP (données réelles)',
    'u87-cell-cycle': 'U-87MG, cycle cellulaire (données réelles)',
  } as Record<string, string>,

  tubes: {
    open: 'Ouvrir des fichiers FCS…',
    examples: 'Exemples :',
    drop: 'Déposez des fichiers FCS ici, ou ouvrez-les avec le bouton ci-dessus.',
    privacy: (minutes: number) =>
      `Les fichiers restent en mémoire sur le serveur, sans être enregistrés, et sont oubliés ` +
      `${minutes} minutes après la dernière action.`,
    simulated:
      'Données simulées : série de doses de cadmium générée par CytoScope pour la démonstration.',
    real:
      'Données réelles : Hassan et al. (2026), cellules U-87MG, Mendeley Data, sous licence ' +
      'CC BY 4.0, présentées sans modification.',
    dataset: 'Jeu de données',
    summary: (tubes: number, groups: number) =>
      `${plural(tubes, 'tube', 'tubes')}, ${plural(groups, 'groupe', 'groupes')}`,
    remove: 'Retirer',
    removeTube: (name: string) => `Retirer le tube ${name}`,
    noStain: '—',
  },

  compensation: {
    source: 'Compensation',
    fromControls: 'Calculer depuis les simples marquages',
    caption:
      'Part du signal de chaque fluorochrome (ligne) lue dans chaque détecteur (colonne), en %. ' +
      'Les valeurs sont compensées par l’inverse de cette matrice. Modifier une case passe la ' +
      'source à « Modifiée à la main ».',
    none: 'Aucune compensation : les valeurs des fluorescences sont utilisées telles quelles.',
    needsTubes: 'Chargez des tubes pour régler la compensation.',
    controls: (count: number) =>
      count === 0
        ? 'Aucun tube de simple marquage : indiquez leur rôle dans l’onglet « Tubes ».'
        : `${plural(count, 'simple marquage', 'simples marquages')} disponible(s).`,
    cell: (from: string, to: string) => `${from} lu dans ${to}`,
  },

  gating: {
    tube: 'Tube',
    population: 'Population affichée',
    x: 'Axe X',
    y: 'Axe Y',
    histogram: '(histogramme)',
    scale: 'Échelle',
    tools: 'Outils',
    tool: {
      select: 'Sélection',
      rectangle: 'Rectangle',
      polygon: 'Polygone',
      quadrant: 'Quadrants',
      interval: 'Intervalle',
    },
    toolTip: {
      select: 'Cliquez une fenêtre pour afficher sa population, glissez-la pour la déplacer.',
      rectangle: 'Glissez pour tracer un rectangle.',
      polygon: 'Cliquez les sommets, puis double-cliquez ou cliquez le premier pour fermer.',
      quadrant: 'Cliquez pour placer la croix des quatre quadrants.',
      interval: 'Glissez pour tracer un intervalle sur l’histogramme.',
    },
    needsHistogram: 'L’intervalle se trace sur un histogramme (axe Y vide).',
    needsDotPlot: 'Cet outil se trace sur un nuage de points : choisissez un axe Y.',
    hierarchy: 'Hiérarchie',
    rename: 'Renommer',
    renamePrompt: 'Nouveau nom',
    remove: 'Supprimer',
    export: 'Exporter en FCS',
    events: (count: number) => plural(count, 'événement', 'événements'),
    defaultName: (n: number) => `Population ${n}`,
    quadrantName: (x: string, y: string, q: Quadrant) =>
      `${x}${quadrantSigns[q][0]} ${y}${quadrantSigns[q][1]}`,
    needsTubes: 'Chargez des tubes pour tracer des fenêtres.',
    shared: 'Les mêmes fenêtres s’appliquent à tous les tubes.',
    plotLabel: (x: string, y: string | null) =>
      y === null ? `Histogramme de ${x}` : `${y} en fonction de ${x}`,
  },

  analysis: {
    apoptosis: 'Test annexine V / IP',
    apoptosisEnable: 'Mesurer l’apoptose',
    apoptosisCaption:
      'Vivantes : annexine V− IP−. Apoptose précoce : annexine V+ IP−. Apoptose tardive : ' +
      'annexine V+ IP+. Nécrotiques : annexine V− IP+. Glissez la croix pour ajuster les seuils.',
    parent: 'Population analysée',
    annexin: 'Canal annexine V',
    pi: 'Canal IP',
    annexinThreshold: 'Seuil annexine V',
    piThreshold: 'Seuil IP',
    suggest: 'Proposer depuis les témoins',
    cellCycle: 'Cycle cellulaire',
    cellCycleEnable: 'Mesurer le cycle cellulaire',
    cellCycleCaption:
      'Les pics G0/G1 et G2/M sont trouvés sur l’histogramme du contenu en ADN ; les phases ' +
      'sont bornées à 2,5 écarts-types autour des pics.',
    dna: 'Canal ADN',
    comparison: 'Comparaison des groupes',
    metric: 'Valeur comparée',
    noMetric: '(aucune)',
    controlGroup: 'Groupe témoin',
    comparisonCaption:
      'ANOVA à un facteur, puis test de Dunnett de chaque groupe contre le témoin. La même ' +
      'valeur sert à la courbe dose-réponse.',
    preview: 'Aperçu',
    needsTubes: 'Chargez des tubes pour régler les analyses.',
  },

  results: {
    refresh: 'Recalculer',
    perTube: 'Résultats par tube',
    comparison: 'Comparaison des groupes',
    doseResponse: 'Dose-réponse',
    anova: (f: string, df1: number, df2: number, p: string) =>
      `ANOVA à un facteur : F(${df1}, ${df2}) = ${f}, p ${p}.`,
    stars: (control: string) =>
      `Étoiles : test de Dunnett contre ${control} (* p < 0,05, ** p < 0,01, *** p < 0,001).`,
    noComparison: (reason: string) => `Les groupes n’ont pas pu être comparés : ${reason}`,
    noCurve: (reason: string) => `Pas de courbe dose-réponse : ${reason}`,
    control: 'témoin',
    needsTubes: 'Chargez des tubes pour voir les résultats.',
    computing: 'Calcul en cours…',
    fit: {
      ec50: 'CE50 [IC 95 %]',
      bottom: 'Plateau bas',
      top: 'Plateau haut',
      hill: 'Pente de Hill',
      rSquared: 'R²',
      points: 'Points',
    },
    dose: 'Dose',
    cv: 'CV G0/G1',
    noPeak: 'pas de pic',
  },

  report: {
    intro:
      'Le rapport réunit les tubes, la compensation, la stratégie de fenêtrage, les figures, ' +
      'les résultats et les statistiques, dans un PDF prêt à partager.',
    title: 'Titre',
    titlePrompt: 'Rapport de cytométrie en flux',
    language: 'Langue',
    analyst: 'Analyste',
    analystPrompt: 'facultatif',
    notes: 'Notes',
    notesPrompt: 'facultatif : protocole, lot de cellules, conclusions…',
    download: 'Télécharger le rapport PDF',
    needsTubes: 'Chargez d’abord des tubes.',
  },

  column: {
    tube: 'Tube',
    role: 'Rôle',
    group: 'Groupe',
    dose: 'Dose',
    events: 'Événements',
    stain: 'Fluorochrome',
    population: 'Population',
    count: 'Événements',
    percentParent: '% du parent',
    percentTotal: '% du total',
    meanSd: 'Moyenne ± é.-t.',
    replicates: 'Réplicats',
    difference: 'Différence',
    pAdjusted: 'p ajusté',
    significance: 'Signif.',
    parameter: 'Paramètre',
    value: 'Valeur',
  },

  tip: {
    tabHelp: 'Aide sur cet onglet (F1)',
    role:
      'Échantillon : tube analysé. Témoin non marqué et simples marquages : servent à la ' +
      'compensation et aux seuils.',
    group: 'Condition du tube ; les réplicats d’une condition portent le même nom de groupe.',
    dose: 'Concentration du traitement, pour la courbe dose-réponse ; vide si sans objet.',
    scale:
      'Logicle et arcsinh affichent les valeurs négatives que produit la compensation, ' +
      'contrairement à l’échelle logarithmique.',
    export: 'Enregistre les événements de cette population, dans le tube affiché, en FCS 3.1.',
    suggest:
      'Place chaque seuil au 99e centile des témoins sans ce marquage : 1 % des cellules non ' +
      'marquées le dépassent.',
  },

  role,
  source,
  scale,
  fate,
  phase,
  issue,

  /** Nom de la valeur comparée entre les groupes. */
  metric: {
    APOPTOTIC: '% de cellules apoptotiques',
    fate: (name: string) => `% de cellules ${name.toLowerCase()}`,
    phase: (name: string) => `% de cellules en ${name}`,
    population: (name: string) => `% de ${name} dans le parent`,
  },

  /** Pourcentage avec une décimale : « 2,1 % ». */
  percent: (value: number | null) => (value === null ? '—' : `${decimals(value, 1)} %`),
  /** Nombre avec un nombre fixe de décimales : « 8,08 ». */
  number: (value: number | null, digits = 2) => (value === null ? '—' : decimals(value, digits)),
  /** Nombre entier avec séparateur des milliers. */
  count: (value: number) => value.toLocaleString('fr-CA'),
  /** Probabilité : « = 0,012 » ou « < 0,001 ». */
  pValue: (p: number) => (p < 0.001 ? '< 0,001' : `= ${decimals(p, 3)}`),

  status: {
    ready: 'Prêt. Ouvrez des fichiers FCS (Ctrl+O) ou une expérience d’exemple. F1 ouvre l’aide.',
    opening: (name: string) => `Ouverture de l’exemple « ${name} »…`,
    opened: (name: string, tubes: number) =>
      `Exemple « ${name} » ouvert : ${plural(tubes, 'tube', 'tubes')}.`,
    loading: (file: string, index: number, total: number) =>
      `Lecture de ${file} (${index} sur ${total})…`,
    loaded: (count: number) => `${plural(count, 'fichier lu', 'fichiers lus')}.`,
    removed: (name: string) => `Tube ${name} retiré.`,
    saved: 'Modification enregistrée.',
    matrix: 'Matrice de compensation calculée à partir des simples marquages.',
    fileMatrix: 'Matrice de compensation des fichiers rétablie.',
    thresholds: 'Seuils proposés à partir des témoins.',
    analyzing: 'Calcul des résultats…',
    analyzed: 'Résultats à jour.',
    writingReport: 'Rédaction du rapport…',
    reportSaved: (file: string) => `Rapport ${file} téléchargé.`,
    cleared: 'Nouvelle expérience : aucun tube chargé.',
    expired: (minutes: number) =>
      `Vos fichiers ont été oubliés par le serveur après ${minutes} minutes d’inactivité ; ` +
      'rechargez-les pour continuer.',
    error: (text: string) => `Erreur : ${text}`,
    errorDetail: (explanation: string, detail: string) => `${explanation} (${detail})`,
  },

  error: {
    parsing: 'fichier FCS invalide',
    invalidInput: 'saisie invalide',
    notFound: 'élément introuvable',
    expired: 'session expirée',
    tooLarge: 'fichier trop volumineux (50 Mo au plus)',
    busy: 'le serveur est très sollicité ; réessayez dans quelques minutes',
    unexpected: 'erreur inattendue',
    server: 'le serveur ne répond pas ; réessayez dans un instant',
    noTubes: 'chargez d’abord des tubes.',
    notFcs: (name: string) => `${name} n’est pas un fichier .fcs.`,
  },

  dialog: { close: 'Fermer' },

  help: {
    title: 'Guide d’utilisation',
    searchLabel: 'Rechercher :',
    searchPrompt: 'mot-clé, ex. compensation, logicle, Dunnett…',
    clear: 'Effacer',
    results: (sections: number, occurrences: number) =>
      `${sections === 0 ? 'aucune section' : plural(sections, 'section', 'sections')} · ` +
      (occurrences === 0 ? 'aucune occurrence' : plural(occurrences, 'occurrence', 'occurrences')),
    none: (query: string) => `Aucune section du guide ne contient « ${query} ».`,
  },

  about: {
    title: (app: string) => `À propos de ${app}`,
    version: (version: string) => `Version ${version}`,
    description:
      'Analyse de cytométrie en flux : lecture des fichiers FCS, compensation, fenêtrage ' +
      'hiérarchique, test annexine V / IP, cycle cellulaire, comparaison statistique des ' +
      'groupes, courbe dose-réponse et rapport PDF.',
    author: (name: string) => `Développé par ${name}, consultant en bio-informatique`,
    website: 'https://www.maximeethier.com',
    sources:
      'Format FCS 2.0, 3.0 et 3.1 (ISAC). Échelle logicle : Parks, Roederer et Moore (2006). ' +
      'Test de Dunnett (1955) ; modèle logistique à quatre paramètres.',
    references:
      'Données d’exemple : Hassan et al. (2026), cellules U-87MG, Mendeley Data, ' +
      'doi:10.17632/d4r5h95356.1, CC BY 4.0. La série de cadmium est simulée.',
    disclaimer:
      'Destiné à la recherche uniquement. Les résultats doivent être confirmés par des ' +
      'méthodes validées.',
    runtime: (react: string) => `React ${react} · calculs sur le serveur Java`,
  },

  shortcuts: {
    keys: 'Touches',
    action: 'Action',
    tabs: 'Afficher l’onglet 1 à 6',
    escape: 'Annuler le tracé en cours ; effacer la recherche, puis fermer le guide',
    helpSearch: 'Rechercher dans le guide',
  },
};
