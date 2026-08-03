package com.example.pantryparty.receipt

/**
 * Receipt shorthand, expanded so [parseReceipt] can hand Spoonacular something it
 * recognizes. Grocery printers squeeze item names into ~20 characters, so
 * "GRN GIANT SWT CRN" is what OCR reads and "green giant sweet corn" is what the
 * ingredient autocomplete can actually match.
 *
 * This table is the single highest-leverage knob on match rate: growing it from real
 * receipts improves results without touching any parsing logic. Keys are lowercase
 * and matched per whitespace-separated token, never as substrings, so adding "crn"
 * can never corrupt an unrelated word that happens to contain those letters.
 */
internal val RECEIPT_ABBREVIATIONS: Map<String, String> = mapOf(
    // --- qualifiers ---
    "org" to "organic",
    "orgnc" to "organic",
    "nat" to "natural",
    "frsh" to "fresh",
    "frz" to "frozen",
    "frzn" to "frozen",
    "cnd" to "canned",
    "drd" to "dried",
    "slcd" to "sliced",
    "slc" to "sliced",
    "shrd" to "shredded",
    "shrdd" to "shredded",
    "dcd" to "diced",
    "chpd" to "chopped",
    "rstd" to "roasted",
    "smkd" to "smoked",
    "unslt" to "unsalted",
    "unsltd" to "unsalted",
    "bnls" to "boneless",
    "skls" to "skinless",
    "sknls" to "skinless",
    "hmstyl" to "homestyle",
    "lg" to "large",
    "sm" to "small",
    "med" to "medium",
    "xl" to "extra large",
    "lt" to "light",
    "reg" to "regular",
    "swt" to "sweet",
    "shrp" to "sharp",

    // --- colours ---
    "grn" to "green",
    "yel" to "yellow",
    "ylw" to "yellow",
    "rd" to "red",
    "wht" to "white",
    "blk" to "black",

    // --- produce ---
    "crn" to "corn",
    "tom" to "tomato",
    "tmto" to "tomato",
    "pot" to "potato",
    "ptto" to "potato",
    "onn" to "onion",
    "onio" to "onion",
    "carr" to "carrot",
    "crrt" to "carrot",
    "broc" to "broccoli",
    "brocc" to "broccoli",
    "caul" to "cauliflower",
    "cuc" to "cucumber",
    "cucu" to "cucumber",
    "lett" to "lettuce",
    "ltce" to "lettuce",
    "spin" to "spinach",
    "mush" to "mushroom",
    "mshrm" to "mushroom",
    "ppr" to "pepper",
    "pepp" to "pepper",
    "garl" to "garlic",
    "avo" to "avocado",
    "avoc" to "avocado",
    "ban" to "banana",
    "bnna" to "banana",
    "apl" to "apple",
    "ornge" to "orange",
    "ornj" to "orange",
    "lmn" to "lemon",
    "lme" to "lime",
    "strw" to "strawberry",
    "strwb" to "strawberry",
    "blbry" to "blueberry",
    "blub" to "blueberry",
    "rasp" to "raspberry",
    "cil" to "cilantro",
    "pars" to "parsley",
    "bsl" to "basil",
    "veg" to "vegetable",
    "vegs" to "vegetables",
    "frt" to "fruit",

    // --- meat & fish ---
    "chkn" to "chicken",
    "chk" to "chicken",
    "brst" to "breast",
    "thgh" to "thigh",
    "grnd" to "ground",
    "grd" to "ground",
    "bf" to "beef",
    "prk" to "pork",
    "slmn" to "salmon",
    "slm" to "salmon",
    "shrmp" to "shrimp",
    "bcn" to "bacon",
    "saus" to "sausage",

    // --- dairy & bakery ---
    "mlk" to "milk",
    "whl" to "whole",
    "chz" to "cheese",
    "chs" to "cheese",
    "chdr" to "cheddar",
    "ched" to "cheddar",
    "mozz" to "mozzarella",
    "parm" to "parmesan",
    "yog" to "yogurt",
    "ygrt" to "yogurt",
    "btr" to "butter",
    "crm" to "cream",
    "sr" to "sour",
    "brd" to "bread",
    "bgl" to "bagel",
    "tort" to "tortilla",

    // --- pantry ---
    "rce" to "rice",
    "psta" to "pasta",
    "spag" to "spaghetti",
    "mac" to "macaroni",
    "crl" to "cereal",
    "cer" to "cereal",
    "flr" to "flour",
    "sgr" to "sugar",
    "slt" to "salt",
    "vngr" to "vinegar",
    "sce" to "sauce",
    "sau" to "sauce",
    "ktchp" to "ketchup",
    "mayo" to "mayonnaise",
    "myo" to "mayonnaise",
    "mstd" to "mustard",
    "pnt" to "peanut",
    "hny" to "honey",
    "choc" to "chocolate",
    "jce" to "juice",
    "wtr" to "water",
    "cof" to "coffee",
    "cfe" to "coffee",
    "bev" to "beverage",
    "crkr" to "cracker",
    "cky" to "cookie",
    "snck" to "snack"
)

/**
 * Store-brand prefixes, dropped rather than expanded. "PC WHL MILK" is milk; feeding
 * Spoonacular the brand only adds noise to the query.
 */
internal val RECEIPT_BRAND_TOKENS: Set<String> = setOf(
    "pc", "nn", "gv", "ks", "comp", "irr", "presidents", "choice",
    "kirkland", "signature", "compliments", "selection", "value"
)

/**
 * Tokens that mark a line as bookkeeping rather than an item. Matched per token
 * (not as substrings), so "SALT" can never trip the "TAX" entry.
 */
internal val RECEIPT_NOISE_TOKENS: Set<String> = setOf(
    "subtotal", "subtot", "total", "tax", "taxes", "hst", "gst", "pst", "qst",
    "cash", "change", "debit", "credit", "visa", "mastercard", "interac", "amex",
    "balance", "tender", "savings", "saved", "discount", "coupon", "loyalty",
    "points", "optimum", "member", "membership", "rewards", "scene", "miles",
    "thank", "thanks", "customer", "store", "tel", "phone", "fax", "www", "http",
    "invoice", "auth", "approved", "terminal", "merchant", "batch", "seq",
    "cashier", "receipt", "survey", "refund", "exchange", "policy", "signature",
    "payment", "account", "card", "chip", "pin", "aid", "tvr", "tsi", "arc",
    "purchase", "amount", "due", "cardholder", "copy", "void", "transaction",
    "tps", "tvq", "tvh",  // Quebec tax lines on Metro/IGA receipts
    // Store and banner names, so the header block doesn't survive as an "item".
    // Covers the chains NFR 8.1 calls out, plus the obvious neighbours.
    "costco", "wholesale", "warehouse", "zehrs", "loblaw", "loblaws", "superstore",
    "rcss", "metro", "sobeys", "safeway", "freshco", "walmart", "yig", "foodland",
    "independent", "nofrills", "frills", "farmboy", "longos", "iga", "maxi",
    "supermarket", "markets", "grocery", "groceries", "ltd", "inc"
)
