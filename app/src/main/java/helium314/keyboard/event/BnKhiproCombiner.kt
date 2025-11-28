// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import java.util.ArrayList

/**
 * Bengali combiner implementing the Khipro state machine.
 * Converts Latin input sequences to Bengali text using greedy longest-match algorithm.
 *
 * This implementation matches the m17n khipro layout with:
 * - All core vowels (shor), consonants (byanjon), and conjuncts (juktoborno)
 * - Minimal punctuation (।ff → ৺)
 *
 * Intentionally excluded:
 * - Number mappings (ongko group)
 * - ZWJ/ZWNJ support
 * - Extended punctuation (currency symbols, math operators)
 */
class BnKhiproCombiner : Combiner {

    private val composingText = StringBuilder()

    enum class State {
        INIT,
        SHOR_STATE,
        REPH_STATE,
        BYANJON_STATE
    }

    companion object {
        // Shor
        private val SHOR = mapOf(
            "o" to "অ", "a" to "আ", "i" to "ই", "ii" to "ঈ",
            "u" to "উ", "uu" to "ঊ", "q" to "ঋ", "e" to "এ",
            "oi" to "ঐ", "w" to "ও", "ou" to "ঔ", "ae" to "অ্যা",
            "wa" to "ওয়া", "wae" to "ওয়্যা", "we" to "ওয়ে",
            "ooo" to "অং",
            "oof" to "ঽ"
        )

        // Fkar
        private val FKAR = mapOf(
            "fuf" to "‌ু", "fuuf" to "‌ূ", "fqf" to "‌ৃ",
            "fa" to "া",
            "fi" to "ি",
            "fii" to "ী",
            "fu" to "ু",
            "fuu" to "ূ",
            "fq" to "ৃ",
            "fe" to "ে",
            "foi" to "ৈ",
            "fw" to "ো",
            "fou" to "ৌ",
            "fae" to "্যা",
            "fwa" to "োয়া",
            "fwe" to "োয়ে",
            "oo" to "ং"
        )

        // Byanjon
        private val BYANJON = mapOf(
            "k" to "ক", "kh" to "খ", "g" to "গ", "gh" to "ঘ",  "ngf" to "ঙ",
            "c" to "চ", "ch" to "ছ", "j" to "জ", "jh" to "ঝ", "nff" to "ঞ",
            "tf" to "ট", "tff" to "ঠ", "df" to "ড", "dff" to "ঢ", "nf" to "ণ",
            "t" to "ত", "th" to "থ", "d" to "দ", "dh" to "ধ", "n" to "ন",
            "p" to "প", "ph" to "ফ", "b" to "ব", "v" to "ভ", "m" to "ম",
            "z" to "য", "l" to "ল", "sh" to "শ", "sf" to "ষ", "s" to "স", "h" to "হ",
            "y" to "য়", "rf" to "ড়", "rff" to "ঢ়",
            ",," to "়"
        )

        // Juktoborno
        private val JUKTOBORNO = mapOf(
            "rz" to "র‍্য",
            "kk" to "ক্ক", "ktf" to "ক্ট", "ktfr" to "ক্ট্র", "kt" to "ক্ত", "ktr" to "ক্ত্র", "kb" to "ক্ব", "km" to "ক্ম", "kz" to "ক্য", "kr" to "ক্র", "kl" to "ক্ল",
            "kf" to "ক্ষ", "ksf" to "ক্ষ", "kkh" to "ক্ষ", "kfnf" to "ক্ষ্ণ", "kfn" to "ক্ষ্ণ", "ksfnf" to "ক্ষ্ণ", "ksfn" to "ক্ষ্ণ", "kkhn" to "ক্ষ্ণ", "kkhnf" to "ক্ষ্ণ",
            "kfb" to "ক্ষ্ব", "ksfb" to "ক্ষ্ব", "kkhb" to "ক্ষ্ব", "kfm" to "ক্ষ্ম", "kkhm" to "ক্ষ্ম", "ksfm" to "ক্ষ্ম", "kfz" to "ক্ষ্য", "ksfz" to "ক্ষ্য", "kkhz" to "ক্ষ্য",
            "ks" to "ক্স",
            "khz" to "খ্য", "khr" to "খ্র",
            "ggg" to "গ্গ", "gnf" to "গ্‌ণ", "gdh" to "গ্ধ", "gdhz" to "গ্ধ্য", "gdhr" to "গ্ধ্র", "gn" to "গ্ন", "gnz" to "গ্ন্য", "gb" to "গ্ব", "gm" to "গ্ম", "gz" to "গ্য", "gr" to "গ্র", "grz" to "গ্র্য", "gl" to "গ্ল",
            "ghn" to "ঘ্ন", "ghr" to "ঘ্র",
            "ngk" to "ঙ্ক", "ngkt" to "ঙ্‌ক্ত", "ngkz" to "ঙ্ক্য", "ngkr" to "ঙ্ক্র", "ngkf" to "ঙ্ক্ষ", "ngkkh" to "ঙ্ক্ষ", "ngksf" to "ঙ্ক্ষ", "ngkh" to "ঙ্খ", "ngg" to "ঙ্গ", "nggz" to "ঙ্গ্য", "nggh" to "ঙ্ঘ", "ngghz" to "ঙ্ঘ্য", "ngghr" to "ঙ্ঘ্র", "ngm" to "ঙ্ম",
            "ngfk" to "ঙ্ক", "ngfkt" to "ঙ্‌ক্ত", "ngfkz" to "ঙ্ক্য", "ngfkr" to "ঙ্ক্র", "ngfkf" to "ঙ্ক্ষ", "ngfkkh" to "ঙ্ক্ষ", "ngfksf" to "ঙ্ক্ষ", "ngfkh" to "ঙ্খ", "ngfg" to "ঙ্গ", "ngfgz" to "ঙ্গ্য", "ngfgh" to "ঙ্ঘ", "ngfghz" to "ঙ্ঘ্য", "ngfghr" to "ঙ্ঘ্র", "ngfm" to "ঙ্ম",
            "nggg" to "ংজ্ঞ",
            "cc" to "চ্চ", "cch" to "চ্ছ", "cchb" to "চ্ছ্ব", "cchr" to "চ্ছ্র", "cnff" to "চ্ঞ", "cb" to "চ্ব", "cz" to "চ্য",
            "jj" to "জ্জ", "jjb" to "জ্জ্ব", "jjh" to "জ্ঝ", "jnff" to "জ্ঞ", "gg" to "জ্ঞ", "jb" to "জ্ব", "jz" to "জ্য", "jr" to "জ্র",
            "nc" to "ঞ্চ", "nffc" to "ঞ্চ", "nj" to "ঞ্জ", "nffj" to "ঞ্জ", "njh" to "ঞ্ঝ", "nffjh" to "ঞ্ঝ", "nch" to "ঞ্ছ", "nffch" to "ঞ্ছ",
            "ttf" to "ট্ট", "tftf" to "ট্ট", "tfb" to "ট্ব", "tfm" to "ট্ম", "tfz" to "ট্য", "tfr" to "ট্র",
            "ddf" to "ড্ড", "dfdf" to "ড্ড", "dfb" to "ড্ব", "dfz" to "ড্য", "dfr" to "ড্র", "rfg" to "ড়্‌গ",
            "dffz" to "ঢ্য", "dffr" to "ঢ্র",
            "nftf" to "ণ্ট", "nftff" to "ণ্ঠ", "nftffz" to "ণ্ঠ্য", "nfdf" to "ণ্ড", "nfdfz" to "ণ্ড্য", "nfdfr" to "ণ্ড্র", "nfdff" to "ণ্ঢ", "nfnf" to "ণ্ণ", "nfn" to "ণ্ণ", "nfb" to "ণ্ব", "nfm" to "ণ্ম", "nfz" to "ণ্য",
            "tt" to "ত্ত", "ttb" to "ত্ত্ব", "ttz" to "ত্ত্য", "tth" to "ত্থ", "tn" to "ত্ন", "tb" to "ত্ব", "tm" to "ত্ম", "tmz" to "ত্ম্য", "tz" to "ত্য", "tr" to "ত্র", "trz" to "ত্র্য",
            "thb" to "থ্ব", "thz" to "থ্য", "thr" to "থ্র",
            "dg" to "দ্‌গ", "dgh" to "দ্‌ঘ", "dd" to "দ্দ", "ddb" to "দ্দ্ব", "ddh" to "দ্ধ", "db" to "দ্ব", "dv" to "দ্ভ", "dvr" to "দ্ভ্র", "dm" to "দ্ম", "dz" to "দ্য", "dr" to "দ্র", "drz" to "দ্র্য",
            "dhn" to "ধ্ন", "dhb" to "ধ্ব", "dhm" to "ধ্ম", "dhz" to "ধ্য", "dhr" to "ধ্র",
            "ntf" to "ন্ট", "ntfr" to "ন্ট্র", "ntff" to "ন্ঠ", "ndf" to "ন্ড", "ndfr" to "ন্ড্র", "nt" to "ন্ত", "ntb" to "ন্ত্ব", "ntr" to "ন্ত্র", "ntrz" to "ন্ত্র্য", "nth" to "ন্থ", "nthr" to "ন্থ্র", "nd" to "ন্দ", "ndb" to "ন্দ্ব", "ndz" to "ন্দ্য",
            "ndr" to "ন্দ্র", "ndh" to "ন্ধ", "ndhz" to "ন্ধ্য", "ndhr" to "ন্ধ্র", "nn" to "ন্ন", "nb" to "ন্ব", "nm" to "ন্ম", "nz" to "ন্য", "ns" to "ন্স", "nstf" to "নস্ট", "nst" to "নস্ত", "nsk" to "নস্ক",
            "ptf" to "প্ট", "pt" to "প্ত", "pn" to "প্ন", "pp" to "প্প", "pz" to "প্য", "pr" to "প্র", "pl" to "প্ল", "ps" to "প্স",
            "phr" to "ফ্র", "phl" to "ফ্ল",
            "bj" to "ব্জ", "bd" to "ব্দ", "bdh" to "ব্ধ", "bb" to "ব্ব", "bz" to "ব্য", "br" to "ব্র", "bl" to "ব্ল", "vb" to "ভ্ব", "vz" to "ভ্য", "vr" to "ভ্র", "vl" to "ভ্ল",
            "mn" to "ম্ন", "mp" to "ম্প", "mpr" to "ম্প্র", "mph" to "ম্ফ", "mb" to "ম্ব", "mbr" to "ম্ব্র", "mv" to "ম্ভ", "mvr" to "ম্ভ্র", "mm" to "ম্ম", "mz" to "ম্য", "mr" to "ম্র", "ml" to "ম্ল",
            "zz" to "য্য",
            "lk" to "ল্ক", "lkz" to "ল্ক্য", "lg" to "ল্গ", "ltf" to "ল্ট", "ldf" to "ল্ড", "lp" to "ল্প", "lph" to "ল্ফ", "lb" to "ল্ব", "lv" to "ল্‌ভ", "lm" to "ল্ম", "lz" to "ল্য", "ll" to "ল্ল",
            "shc" to "শ্চ", "shch" to "শ্ছ", "shn" to "শ্ন", "shb" to "শ্ব", "shm" to "শ্ম", "shz" to "শ্য", "shr" to "শ্র", "shl" to "শ্ল",
            "sfk" to "ষ্ক", "sfkr" to "ষ্ক্র", "sftf" to "ষ্ট", "sftfz" to "ষ্ট্য", "sftfr" to "ষ্ট্র", "sftff" to "ষ্ঠ", "sftffz" to "ষ্ঠ্য", "sfnf" to "ষ্ণ", "sfn" to "ষ্ণ",
            "sfp" to "ষ্প", "sfpr" to "ষ্প্র", "sfph" to "ষ্ফ", "sfb" to "ষ্ব", "sfm" to "ষ্ম", "sfz" to "ষ্য",
            "sk" to "স্ক", "skr" to "স্ক্র", "skh" to "স্খ", "stf" to "স্ট", "stfr" to "স্ট্র", "st" to "স্ত", "stb" to "স্ত্ব", "stz" to "স্ত্য", "str" to "স্ত্র", "sth" to "স্থ", "sthz" to "স্থ্য", "sn" to "স্ন",
            "sp" to "স্প", "spr" to "স্প্র", "spl" to "স্প্ল", "sph" to "স্ফ", "sb" to "স্ব", "sm" to "স্ম", "sz" to "স্য", "sr" to "স্র", "sl" to "স্ল",
            "hn" to "হ্ন", "hnf" to "হ্ণ", "hb" to "হ্ব", "hm" to "হ্ম", "hz" to "হ্য", "hr" to "হ্র", "hl" to "হ্ল",

            // oshomvob juktoborno
            "ksh" to "কশ", "kks" to "কক্স", "nsh" to "নশ", "psh" to "পশ", "ld" to "লদ", "gd" to "গদ", "ngkk" to "ঙ্কক", "ngks" to "ঙ্কস", "tft" to "টত", "dfd" to "ডদ",
            
            // CN combinations
            "cn" to "চন",
            "cnf" to "চণ", "cnz" to "চন্য", "cnm" to "চন্ম",
            "cnc" to "চঞ্চ", "cnffc" to "চঞ্চ", "cnj" to "চঞ্জ", "cnffj" to "চঞ্জ", "cnjh" to "চঞ্ঝ", "cnffjh" to "চঞ্ঝ", "cnch" to "চঞ্ছ", "cnffch" to "চঞ্ছ",
            "cnftf" to "চণ্ট", "cnftff" to "চণ্ঠ", "cnftffz" to "চণ্ঠ্য", "cnfdf" to "চণ্ড", "cnfdfz" to "চণ্ড্য", "cnfdfr" to "চণ্ড্র", "cnfdff" to "চণ্ঢ", "cnfnf" to "চণ্ণ", "cnfn" to "চণ্ণ", "cnfb" to "চণ্ব", "cnfm" to "চণ্ম", "cnfz" to "চণ্য",
            "cntf" to "চন্ট", "cntfr" to "চন্ট্র", "cntff" to "চন্ঠ", "cndf" to "চন্ড", "cndfr" to "চন্ড্র", "cnt" to "চন্ত", "cntb" to "চন্ত্ব", "cntr" to "চন্ত্র", "cntrz" to "চন্ত্র্য", "cnth" to "চন্থ", "cnthr" to "চন্থ্র", "cnd" to "চন্দ", "cndb" to "চন্দ্ব", "cndz" to "চন্দ্য",
            "cndr" to "চন্দ্র", "cndh" to "চন্ধ", "cndhz" to "চন্ধ্য", "cndhr" to "চন্ধ্র", "cnn" to "চন্ন", "cnb" to "চন্ব", "cns" to "চন্স",
            "cngksh" to "চঙ্কশ", "cngfksh" to "চঙ্কশ",
            "cngkk" to "চঙ্কক", "cngks" to "চঙ্কস", "cngfkk" to "চঙ্কক", "cngfks" to "চঙ্কস",
            "cnft" to "চণত", "cnfd" to "চণদ", "cnfth" to "চণথ", "cnfdh" to "চণধ",
            "cndff" to "চনঢ",
            "cnfdfrf" to "চণ্ডড়", "cnfdfrff" to "চণ্ডঢ়",
            "cntfrf" to "চন্টড়", "cntfrff" to "চন্টঢ়", "cndfrf" to "চন্ডড়", "cndfrff" to "চন্ডঢ়", "cntrf" to "চন্তড়", "cntrff" to "চন্তঢ়", "cnthrf" to "চন্থড়",
            "cnstf" to "চনস্ট", "cnst" to "চনস্ত", "cnsk" to "চনস্ক",
            "cnthrff" to "চন্থঢ়", "cndrf" to "চন্দড়", "cndrff" to "চন্দঢ়", "cndhrf" to "চন্ধড়", "cndhrff" to "চন্ধঢ়",
            "cnsh" to "চনশ",
            
            // JN combinations
            "jn" to "জন",
            "jnf" to "জণ", "jnz" to "জন্য", "jnm" to "জন্ম",
            "jnc" to "জঞ্চ", "jnffc" to "জঞ্চ", "jnj" to "জঞ্জ", "jnffj" to "জঞ্জ", "jnjh" to "জঞ্ঝ", "jnffjh" to "জঞ্ঝ", "jnch" to "জঞ্ছ", "jnffch" to "জঞ্ছ",
            "jnftf" to "জণ্ট", "jnftff" to "জণ্ঠ", "jnftffz" to "জণ্ঠ্য", "jnfdf" to "জণ্ড", "jnfdfz" to "জণ্ড্য", "jnfdfr" to "জণ্ড্র", "jnfdff" to "জণ্ঢ", "jnfnf" to "জণ্ণ", "jnfn" to "জণ্ণ", "jnfb" to "জণ্ব", "jnfm" to "জণ্ম", "jnfz" to "জণ্য",
            "jntf" to "জন্ট", "jntfr" to "জন্ট্র", "jntff" to "জন্ঠ", "jndf" to "জন্ড", "jndfr" to "জন্ড্র", "jnt" to "জন্ত", "jntb" to "জন্ত্ব", "jntr" to "জন্ত্র", "jntrz" to "জন্ত্র্য", "jnth" to "জন্থ", "jnthr" to "জন্থ্র", "jnd" to "জন্দ", "jndb" to "জন্দ্ব", "jndz" to "জন্দ্য",
            "jndr" to "জন্দ্র", "jndh" to "জন্ধ", "jndhz" to "জন্ধ্য", "jndhr" to "জন্ধ্র", "jnn" to "জন্ন", "jnb" to "জন্ব", "jns" to "জন্স",
            "jngksh" to "জঙ্কশ", "jngfksh" to "জঙ্কশ",
            "jngkk" to "জঙ্কক", "jngks" to "জঙ্কস", "jngfkk" to "জঙ্কক", "jngfks" to "জঙ্কস",
            "jnft" to "জণত", "jnfd" to "জণদ", "jnfth" to "জণথ", "jnfdh" to "জণধ",
            "jndff" to "জনঢ",
            "jnfdfrf" to "জণ্ডড়", "jnfdfrff" to "জণ্ডঢ়",
            "jntfrf" to "জন্টড়", "jntfrff" to "জন্টঢ়", "jndfrf" to "জন্ডড়", "jndfrff" to "জন্ডঢ়", "jntrf" to "জন্তড়", "jntrff" to "জন্তঢ়", "jnthrf" to "জন্থড়",
            "jnstf" to "জনস্ট", "jnst" to "জনস্ত", "jnsk" to "জনস্ক",
            "jnthrff" to "জন্থঢ়", "jndrf" to "জন্দড়", "jndrff" to "জন্দঢ়", "jndhrf" to "জন্ধড়", "jndhrff" to "জন্ধঢ়",
            "jnsh" to "জনশ",
            "nft" to "ণত", "nfd" to "ণদ", "lt" to "লত", "sft" to "ষত", "nfth" to "ণথ", "nfdh" to "ণধ", "sfth" to "ষথ",
            "ktff" to "কঠ", "ptff" to "পঠ", "ltff" to "লঠ", "stff" to "সঠ", "dfdff" to "ডঢ", "ndff" to "নঢ",
            "ktfrf" to "ক্টড়", "ktfrff" to "ক্টঢ়", "kth" to "কথ", "ktrf" to "ক্তড়", "ktrff" to "ক্তঢ়", "krf" to "কড়", "krff" to "কঢ়", "khrf" to "খড়", "khrfg" to "খড়্‌গ", "khrff" to "খঢ়", "gggh" to "জ্ঞঘ", "gdff" to "গঢ", "gdhrf" to "গ্ধড়",
            "gdhrff" to "গ্ধঢ়", "grf" to "গড়", "grff" to "গঢ়", "ghrf" to "ঘড়", "ghrff" to "ঘঢ়", "ngkth" to "ঙ্কথ", "ngkrf" to "ঙ্কড়", "ngkrff" to "ঙ্কঢ়", "ngghrf" to "ঙ্ঘড়", "ngghrff" to "ঙ্ঘঢ়", "cchrf" to "চ্ছড়", "cchrff" to "চ্ছঢ়",
            "tfrf" to "টড়", "tfrff" to "টঢ়", "dfrf" to "ডড়", "dfrff" to "ডঢ়", "rfgh" to "ড়ঘ", "dffrf" to "ঢড়", "dffrff" to "ঢঢ়", "nfdfrf" to "ণ্ডড়", "nfdfrff" to "ণ্ডঢ়", "trf" to "তড়", "trff" to "তঢ়", "thrf" to "থড়", "thrff" to "থঢ়",
            "dvrf" to "দ্ভড়", "dvrff" to "দ্ভঢ়", "drf" to "দড়", "drff" to "দঢ়", "dhrf" to "ধড়", "dhrff" to "ধঢ়", "ntfrf" to "ন্টড়", "ntfrff" to "ন্টঢ়", "ndfrf" to "ন্ডড়", "ndfrff" to "ন্ডঢ়", "ntrf" to "ন্তড়", "ntrff" to "ন্তঢ়", "nthrf" to "ন্থড়",
            "nthrff" to "ন্থঢ়", "ndrf" to "ন্দড়", "ndrff" to "ন্দঢ়", "ndhrf" to "ন্ধড়", "ndhrff" to "ন্ধঢ়", "pth" to "পথ", "pph" to "পফ", "prf" to "পড়", "prff" to "পঢ়", "phrf" to "ফড়", "phrff" to "ফঢ়", "bjh" to "বঝ", "brf" to "বড়", "brff" to "বঢ়",
            "vrf" to "ভড়", "vrff" to "ভঢ়", "mprf" to "ম্পড়", "mprff" to "ম্পঢ়", "mbrf" to "ম্বড়", "mbrff" to "ম্বঢ়", "mvrf" to "ম্ভড়", "mvrff" to "ম্ভঢ়", "mrf" to "মড়", "mrff" to "মঢ়", "lkh" to "লখ", "lgh" to "লঘ", "shrf" to "শড়", "shrff" to "শঢ়", "sfkh" to "ষখ",
            "sfkrf" to "ষ্কড়", "sfkrff" to "ষ্কঢ়", "sftfrf" to "ষ্টড়", "sftfrff" to "ষ্টঢ়", "sfprf" to "ষ্পড়", "sfprff" to "ষ্পঢ়", "skrf" to "স্কড়", "skrff" to "স্কঢ়", "stfrf" to "স্টড়", "stfrff" to "স্টঢ়", "strf" to "স্তড়", "strff" to "স্তঢ়", "sprf" to "স্পড়", "sprff" to "স্পঢ়",
            "srf" to "সড়", "srff" to "সঢ়", "hrf" to "হড়", "hrff" to "হঢ়", "ldh" to "লধ", "ngksh" to "ঙ্কশ", "tfth" to "টথ", "dfdh" to "ডধ", "lth" to "লথ",
            "ngfkk" to "ঙ্কক", "ngfks" to "ঙ্কস", "ngfkth" to "ঙ্কথ", "ngfkrf" to "ঙ্কড়", "ngfkrff" to "ঙ্কঢ়", "ngfghrf" to "ঙ্ঘড়", "ngfghrff" to "ঙ্ঘঢ়", "ngfksh" to "ঙ্কশ",
            "kkf" to "কক্ষ", "lkf" to "লক্ষ", "sfkf" to "ষক্ষ", "skf" to "সক্ষ", "kkkh" to "কক্ষ", "lkkh" to "লক্ষ", "sfkkh" to "ষক্ষ", "skkh" to "সক্ষ", "kksf" to "কক্ষ", "lksf" to "লক্ষ", "sfksf" to "ষক্ষ", "sksf" to "সক্ষ",
            "lks" to "ল্কস",
            "mpl" to "মপ্ল",
            "yr" to "য়র",
            
            // Common word combinations
            "gnj" to "গঞ্জ", "pnj" to "পঞ্জ", "mnj" to "মঞ্জ", "snj" to "সঞ্জ",
            "gndf" to "গন্ড", "mndf" to "মন্ড",
            "tnt" to "তন্ত", "tntr" to "তন্ত্র", "mnt" to "মন্ত", "mntr" to "মন্ত্র", "snt" to "সন্ত", "sntr" to "সন্ত্র", "hnt" to "হন্ত",
            "tnd" to "তন্দ", "nnd" to "নন্দ", "mnd" to "মন্দ", "snd" to "সন্দ",
            "gndh" to "গন্ধ", "gndhz" to "গন্ধ্য", "sndh" to "সন্ধ", "sndhz" to "সন্ধ্য",

		    "gngf" to "গঙ", "gngk" to "গঙ্ক", "gngkt" to "গঙ্‌ক্ত", "gngkz" to "গঙ্ক্য", "gngkr" to "গঙ্ক্র", "gngkf" to "গঙ্ক্ষ", "gngkkh" to "গঙ্ক্ষ", "gngksf" to "গঙ্ক্ষ", "gngkh" to "গঙ্খ", "gngg" to "গঙ্গ", "gnggz" to "গঙ্গ্য", "gnggh" to "গঙ্ঘ", "gngghz" to "গঙ্ঘ্য", "gngghr" to "গঙ্ঘ্র", "gngm" to "গঙ্ম",
		    "gngfk" to "গঙ্ক", "gngfkt" to "গঙ্‌ক্ত", "gngfkz" to "গঙ্ক্য", "gngfkr" to "গঙ্ক্র", "gngfkf" to "গঙ্ক্ষ", "gngfkkh" to "গঙ্ক্ষ", "gngfksf" to "গঙ্ক্ষ", "gngfkh" to "গঙ্খ", "gngfg" to "গঙ্গ", "gngfgz" to "গঙ্গ্য", "gngfgh" to "গঙ্ঘ", "gngfghz" to "গঙ্ঘ্য", "gngfghr" to "গঙ্ঘ্র", "gngfm" to "গঙ্ম",
		    "gnggg" to "গংজ্ঞ",
		    "gngkth" to "গঙ্কথ", "gngkrf" to "গঙ্কড়", "gngkrff" to "গঙ্কঢ়", "gngghrf" to "গঙ্ঘড়", "gngghrff" to "গঙ্ঘঢ়",
		    "gngfkth" to "গঙ্কথ", "gngfkrf" to "গঙ্কড়", "gngfkrff" to "গঙ্কঢ়", "gngfghrf" to "গঙ্ঘড়", "gngfghrff" to "গঙ্ঘঢ়",

		    "ghngf" to "ঘঙ", "ghngk" to "ঘঙ্ক", "ghngkt" to "ঘঙ্‌ক্ত", "ghngkz" to "ঘঙ্ক্য", "ghngkr" to "ঘঙ্ক্র", "ghngkf" to "ঘঙ্ক্ষ", "ghngkkh" to "ঘঙ্ক্ষ", "ghngksf" to "ঘঙ্ক্ষ", "ghngkh" to "ঘঙ্খ", "ghngg" to "ঘঙ্গ", "ghnggz" to "ঘঙ্গ্য", "ghnggh" to "ঘঙ্ঘ", "ghngghz" to "ঘঙ্ঘ্য", "ghngghr" to "ঘঙ্ঘ্র", "ghngm" to "ঘঙ্ম",
		    "ghngfk" to "ঘঙ্ক", "ghngfkt" to "ঘঙ্‌ক্ত", "ghngfkz" to "ঘঙ্ক্য", "ghngfkr" to "ঘঙ্ক্র", "ghngfkf" to "ঘঙ্ক্ষ", "ghngfkkh" to "ঘঙ্ক্ষ", "ghngfksf" to "ঘঙ্ক্ষ", "ghngfkh" to "ঘঙ্খ", "ghngfg" to "ঘঙ্গ", "ghngfgz" to "ঘঙ্গ্য", "ghngfgh" to "ঘঙ্ঘ", "ghngfghz" to "ঘঙ্ঘ্য", "ghngfghr" to "ঘঙ্ঘ্র", "ghngfm" to "ঘঙ্ম",
		    "ghnggg" to "ঘংজ্ঞ",
		    "ghngkth" to "ঘঙ্কথ", "ghngkrf" to "ঘঙ্কড়", "ghngkrff" to "ঘঙ্কঢ়", "ghngghrf" to "ঘঙ্ঘড়", "ghngghrff" to "ঘঙ্ঘঢ়",
		    "ghngfkth" to "ঘঙ্কথ", "ghngfkrf" to "ঘঙ্কড়", "ghngfkrff" to "ঘঙ্কঢ়", "ghngfghrf" to "ঘঙ্ঘড়", "ghngfghrff" to "ঘঙ্ঘঢ়",

		    "cngf" to "চঙ", "cngk" to "চঙ্ক", "cngkt" to "চঙ্‌ক্ত", "cngkz" to "চঙ্ক্য", "cngkr" to "চঙ্ক্র", "cngkf" to "চঙ্ক্ষ", "cngkkh" to "চঙ্ক্ষ", "cngksf" to "চঙ্ক্ষ", "cngkh" to "চঙ্খ", "cngg" to "চঙ্গ", "cnggz" to "চঙ্গ্য", "cnggh" to "চঙ্ঘ", "cngghz" to "চঙ্ঘ্য", "cngghr" to "চঙ্ঘ্র", "cngm" to "চঙ্ম",
		    "cngfk" to "চঙ্ক", "cngfkt" to "চঙ্‌ক্ত", "cngfkz" to "চঙ্ক্য", "cngfkr" to "চঙ্ক্র", "cngfkf" to "চঙ্ক্ষ", "cngfkkh" to "চঙ্ক্ষ", "cngfksf" to "চঙ্ক্ষ", "cngfkh" to "চঙ্খ", "cngfg" to "চঙ্গ", "cngfgz" to "চঙ্গ্য", "cngfgh" to "চঙ্ঘ", "cngfghz" to "চঙ্ঘ্য", "cngfghr" to "চঙ্ঘ্র", "cngfm" to "চঙ্ম",
		    "cnggg" to "চংজ্ঞ",
		    "cngkth" to "চঙ্কথ", "cngkrf" to "চঙ্কড়", "cngkrff" to "চঙ্কঢ়", "cngghrf" to "চঙ্ঘড়", "cngghrff" to "চঙ্ঘঢ়",
		    "cngfkth" to "চঙ্কথ", "cngfkrf" to "চঙ্কড়", "cngfkrff" to "চঙ্কঢ়", "cngfghrf" to "চঙ্ঘড়", "cngfghrff" to "চঙ্ঘঢ়",

		    "jngf" to "জঙ", "jngk" to "জঙ্ক", "jngkt" to "জঙ্‌ক্ত", "jngkz" to "জঙ্ক্য", "jngkr" to "জঙ্ক্র", "jngkf" to "জঙ্ক্ষ", "jngkkh" to "জঙ্ক্ষ", "jngksf" to "জঙ্ক্ষ", "jngkh" to "জঙ্খ", "jngg" to "জঙ্গ", "jnggz" to "জঙ্গ্য", "jnggh" to "জঙ্ঘ", "jngghz" to "জঙ্ঘ্য", "jngghr" to "জঙ্ঘ্র", "jngm" to "জঙ্ম",
		    "jngfk" to "জঙ্ক", "jngfkt" to "জঙ্‌ক্ত", "jngfkz" to "জঙ্ক্য", "jngfkr" to "জঙ্ক্র", "jngfkf" to "জঙ্ক্ষ", "jngfkkh" to "জঙ্ক্ষ", "jngfksf" to "জঙ্ক্ষ", "jngfkh" to "জঙ্খ", "jngfg" to "জঙ্গ", "jngfgz" to "জঙ্গ্য", "jngfgh" to "জঙ্ঘ", "jngfghz" to "জঙ্ঘ্য", "jngfghr" to "জঙ্ঘ্র", "jngfm" to "জঙ্ম",
		    "jnggg" to "জংজ্ঞ",
		    "jngkth" to "জঙ্কথ", "jngkrf" to "জঙ্কড়", "jngkrff" to "জঙ্কঢ়", "jngghrf" to "জঙ্ঘড়", "jngghrff" to "জঙ্ঘঢ়",
		    "jngfkth" to "জঙ্কথ", "jngfkrf" to "জঙ্কড়", "jngfkrff" to "জঙ্কঢ়", "jngfghrf" to "জঙ্ঘড়", "jngfghrff" to "জঙ্ঘঢ়",

		    "nngf" to "নঙ", "nngk" to "নঙ্ক", "nngkt" to "নঙ্‌ক্ত", "nngkz" to "নঙ্ক্য", "nngkr" to "নঙ্ক্র", "nngkf" to "নঙ্ক্ষ", "nngkkh" to "নঙ্ক্ষ", "nngksf" to "নঙ্ক্ষ", "nngkh" to "নঙ্খ", "nngg" to "নঙ্গ", "nnggz" to "নঙ্গ্য", "nnggh" to "নঙ্ঘ", "nngghz" to "নঙ্ঘ্য", "nngghr" to "নঙ্ঘ্র", "nngm" to "নঙ্ম",
		    "nngfk" to "নঙ্ক", "nngfkt" to "নঙ্‌ক্ত", "nngfkz" to "নঙ্ক্য", "nngfkr" to "নঙ্ক্র", "nngfkf" to "নঙ্ক্ষ", "nngfkkh" to "নঙ্ক্ষ", "nngfksf" to "নঙ্ক্ষ", "nngfkh" to "নঙ্খ", "nngfg" to "নঙ্গ", "nngfgz" to "নঙ্গ্য", "nngfgh" to "নঙ্ঘ", "nngfghz" to "নঙ্ঘ্য", "nngfghr" to "নঙ্ঘ্র", "nngfm" to "নঙ্ম",
		    "nnggg" to "নংজ্ঞ",
		    "nngkth" to "নঙ্কথ", "nngkrf" to "নঙ্কড়", "nngkrff" to "নঙ্কঢ়", "nngghrf" to "নঙ্ঘড়", "nngghrff" to "নঙ্ঘঢ়",
		    "nngfkth" to "নঙ্কথ", "nngfkrf" to "নঙ্কড়", "nngfkrff" to "নঙ্কঢ়", "nngfghrf" to "নঙ্ঘড়", "nngfghrff" to "নঙ্ঘঢ়",

		    "nfngf" to "ণঙ", "nfgk" to "ণঙ্ক", "nfgkt" to "ণঙ্‌ক্ত", "nfgkz" to "ণঙ্ক্য", "nfgkr" to "ণঙ্ক্র", "nfgkf" to "ণঙ্ক্ষ", "nfgkkh" to "ণঙ্ক্ষ", "nfgksf" to "ণঙ্ক্ষ", "nfgkh" to "ণঙ্খ", "nfgg" to "ণঙ্গ", "nfggz" to "ণঙ্গ্য", "nfggh" to "ণঙ্ঘ", "nfgghz" to "ণঙ্ঘ্য", "nfgghr" to "ণঙ্ঘ্র", "nfgm" to "ণঙ্ম",
		    "nfgfk" to "ণঙ্ক", "nfgfkt" to "ণঙ্‌ক্ত", "nfgfkz" to "ণঙ্ক্য", "nfgfkr" to "ণঙ্ক্র", "nfgfkf" to "ণঙ্ক্ষ", "nfgfkkh" to "ণঙ্ক্ষ", "nfgfksf" to "ণঙ্ক্ষ", "nfgfkh" to "ণঙ্খ", "nfgfg" to "ণঙ্গ", "nfgfgz" to "ণঙ্গ্য", "nfgfgh" to "ণঙ্ঘ", "nfgfghz" to "ণঙ্ঘ্য", "nfgfghr" to "ণঙ্ঘ্র", "nfgfm" to "ণঙ্ম",
		    "nfggg" to "ণংজ্ঞ",
		    "nfngkth" to "ণঙ্কথ", "nfngkrf" to "ণঙ্কড়", "nfngkrff" to "ণঙ্কঢ়", "nfngghrf" to "ণঙ্ঘড়", "nfngghrff" to "ণঙ্ঘঢ়",
		    "nfngfkth" to "ণঙ্কথ", "nfngfkrf" to "ণঙ্কড়", "nfngfkrff" to "ণঙ্কঢ়", "nfngfghrf" to "ণঙ্ঘড়", "nfngfghrff" to "ণঙ্ঘঢ়",

		    "tngf" to "তঙ", "tngk" to "তঙ্ক", "tngkt" to "তঙ্‌ক্ত", "tngkz" to "তঙ্ক্য", "tngkr" to "তঙ্ক্র", "tngkf" to "তঙ্ক্ষ", "tngkkh" to "তঙ্ক্ষ", "tngksf" to "তঙ্ক্ষ", "tngkh" to "তঙ্খ", "tngg" to "তঙ্গ", "tnggz" to "তঙ্গ্য", "tnggh" to "তঙ্ঘ", "tngghz" to "তঙ্ঘ্য", "tngghr" to "তঙ্ঘ্র", "tngm" to "তঙ্ম",
		    "tngfk" to "তঙ্ক", "tngfkt" to "তঙ্‌ক্ত", "tngfkz" to "তঙ্ক্য", "tngfkr" to "তঙ্ক্র", "tngfkf" to "তঙ্ক্ষ", "tngfkkh" to "তঙ্ক্ষ", "tngfksf" to "তঙ্ক্ষ", "tngfkh" to "তঙ্খ", "tngfg" to "তঙ্গ", "tngfgz" to "তঙ্গ্য", "tngfgh" to "তঙ্ঘ", "tngfghz" to "তঙ্ঘ্য", "tngfghr" to "তঙ্ঘ্র", "tngfm" to "তঙ্ম",
		    "tnggg" to "তংজ্ঞ",
		    "tngkth" to "তঙ্কথ", "tngkrf" to "তঙ্কড়", "tngkrff" to "তঙ্কঢ়", "tngghrf" to "তঙ্ঘড়", "tngghrff" to "তঙ্ঘঢ়",
		    "tngfkth" to "তঙ্কথ", "tngfkrf" to "তঙ্কড়", "tngfkrff" to "তঙ্কঢ়", "tngfghrf" to "তঙ্ঘড়", "tngfghrff" to "তঙ্ঘঢ়",

		    "dhngf" to "ধঙ", "dhngk" to "ধঙ্ক", "dhngkt" to "ধঙ্‌ক্ত", "dhngkz" to "ধঙ্ক্য", "dhngkr" to "ধঙ্ক্র", "dhngkf" to "ধঙ্ক্ষ", "dhngkkh" to "ধঙ্ক্ষ", "dhngksf" to "ধঙ্ক্ষ", "dhngkh" to "ধঙ্খ", "dhngg" to "ধঙ্গ", "dhnggz" to "ধঙ্গ্য", "dhnggh" to "ধঙ্ঘ", "dhngghz" to "ধঙ্ঘ্য", "dhngghr" to "ধঙ্ঘ্র", "dhngm" to "ধঙ্ম",
		    "dhngfk" to "ধঙ্ক", "dhngfkt" to "ধঙ্‌ক্ত", "dhngfkz" to "ধঙ্ক্য", "dhngfkr" to "ধঙ্ক্র", "dhngfkf" to "ধঙ্ক্ষ", "dhngfkkh" to "ধঙ্ক্ষ", "dhngfksf" to "ধঙ্ক্ষ", "dhngfkh" to "ধঙ্খ", "dhngfg" to "ধঙ্গ", "dhngfgz" to "ধঙ্গ্য", "dhngfgh" to "ধঙ্ঘ", "dhngfghz" to "ধঙ্ঘ্য", "dhngfghr" to "ধঙ্ঘ্র", "dhngfm" to "ধঙ্ম",
		    "dhnggg" to "ধংজ্ঞ",
		    "dhngkth" to "ধঙ্কথ", "dhngkrf" to "ধঙ্কড়", "dhngkrff" to "ধঙ্কঢ়", "dhngghrf" to "ধঙ্ঘড়", "dhngghrff" to "ধঙ্ঘঢ়",
		    "dhngfkth" to "ধঙ্কথ", "dhngfkrf" to "ধঙ্কড়", "dhngfkrff" to "ধঙ্কঢ়", "dhngfghrf" to "ধঙ্ঘড়", "dhngfghrff" to "ধঙ্ঘঢ়",

		    "pngf" to "পঙ", "pngk" to "পঙ্ক", "pngkt" to "পঙ্‌ক্ত", "pngkz" to "পঙ্ক্য", "pngkr" to "পঙ্ক্র", "pngkf" to "পঙ্ক্ষ", "pngkkh" to "পঙ্ক্ষ", "pngksf" to "পঙ্ক্ষ", "pngkh" to "পঙ্খ", "pngg" to "পঙ্গ", "pnggz" to "পঙ্গ্য", "pnggh" to "পঙ্ঘ", "pngghz" to "পঙ্ঘ্য", "pngghr" to "পঙ্ঘ্র", "pngm" to "পঙ্ম",
		    "pngfk" to "পঙ্ক", "pngfkt" to "পঙ্‌ক্ত", "pngfkz" to "পঙ্ক্য", "pngfkr" to "পঙ্ক্র", "pngfkf" to "পঙ্ক্ষ", "pngfkkh" to "পঙ্ক্ষ", "pngfksf" to "পঙ্ক্ষ", "pngfkh" to "পঙ্খ", "pngfg" to "পঙ্গ", "pngfgz" to "পঙ্গ্য", "pngfgh" to "পঙ্ঘ", "pngfghz" to "পঙ্ঘ্য", "pngfghr" to "পঙ্ঘ্র", "pngfm" to "পঙ্ম",
		    "pnggg" to "পংজ্ঞ",
		    "pngkth" to "পঙ্কথ", "pngkrf" to "পঙ্কড়", "pngkrff" to "পঙ্কঢ়", "pngghrf" to "পঙ্ঘড়", "pngghrff" to "পঙ্ঘঢ়",
		    "pngfkth" to "পঙ্কথ", "pngfkrf" to "পঙ্কড়", "pngfkrff" to "পঙ্কঢ়", "pngfghrf" to "পঙ্ঘড়", "pngfghrff" to "পঙ্ঘঢ়",

		    "mngf" to "মঙ", "mngk" to "মঙ্ক", "mngkt" to "মঙ্‌ক্ত", "mngkz" to "মঙ্ক্য", "mngkr" to "মঙ্ক্র", "mngkf" to "মঙ্ক্ষ", "mngkkh" to "মঙ্ক্ষ", "mngksf" to "মঙ্ক্ষ", "mngkh" to "মঙ্খ", "mngg" to "মঙ্গ", "mnggz" to "মঙ্গ্য", "mnggh" to "মঙ্ঘ", "mngghz" to "মঙ্ঘ্য", "mngghr" to "মঙ্ঘ্র", "mngm" to "মঙ্ম",
		    "mngfk" to "মঙ্ক", "mngfkt" to "মঙ্‌ক্ত", "mngfkz" to "মঙ্ক্য", "mngfkr" to "মঙ্ক্র", "mngfkf" to "মঙ্ক্ষ", "mngfkkh" to "মঙ্ক্ষ", "mngfksf" to "মঙ্ক্ষ", "mngfkh" to "মঙ্খ", "mngfg" to "মঙ্গ", "mngfgz" to "মঙ্গ্য", "mngfgh" to "মঙ্ঘ", "mngfghz" to "মঙ্ঘ্য", "mngfghr" to "মঙ্ঘ্র", "mngfm" to "মঙ্ম",
		    "mnggg" to "মংজ্ঞ",
		    "mngkth" to "মঙ্কথ", "mngkrf" to "মঙ্কড়", "mngkrff" to "মঙ্কঢ়", "mngghrf" to "মঙ্ঘড়", "mngghrff" to "মঙ্ঘঢ়",
		    "mngfkth" to "মঙ্কথ", "mngfkrf" to "মঙ্কড়", "mngfkrff" to "মঙ্কঢ়", "mngfghrf" to "মঙ্ঘড়", "mngfghrff" to "মঙ্ঘঢ়",

		    "shngf" to "শঙ", "shngk" to "শঙ্ক", "shngkt" to "শঙ্‌ক্ত", "shngkz" to "শঙ্ক্য", "shngkr" to "শঙ্ক্র", "shngkf" to "শঙ্ক্ষ", "shngkkh" to "শঙ্ক্ষ", "shngksf" to "শঙ্ক্ষ", "shngkh" to "শঙ্খ", "shngg" to "শঙ্গ", "shnggz" to "শঙ্গ্য", "shnggh" to "শঙ্ঘ", "shngghz" to "শঙ্ঘ্য", "shngghr" to "শঙ্ঘ্র", "shngm" to "শঙ্ম",
		    "shngfk" to "শঙ্ক", "shngfkt" to "শঙ্‌ক্ত", "shngfkz" to "শঙ্ক্য", "shngfkr" to "শঙ্ক্র", "shngfkf" to "শঙ্ক্ষ", "shngfkkh" to "শঙ্ক্ষ", "shngfksf" to "শঙ্ক্ষ", "shngfkh" to "শঙ্খ", "shngfg" to "শঙ্গ", "shngfgz" to "শঙ্গ্য", "shngfgh" to "শঙ্ঘ", "shngfghz" to "শঙ্ঘ্য", "shngfghr" to "শঙ্ঘ্র", "shngfm" to "শঙ্ম",
		    "shnggg" to "শংজ্ঞ",
		    "shngkth" to "শঙ্কথ", "shngkrf" to "শঙ্কড়", "shngkrff" to "শঙ্কঢ়", "shngghrf" to "শঙ্ঘড়", "shngghrff" to "শঙ্ঘঢ়",
		    "shngfkth" to "শঙ্কথ", "shngfkrf" to "শঙ্কড়", "shngfkrff" to "শঙ্কঢ়", "shngfghrf" to "শঙ্ঘড়", "shngfghrff" to "শঙ্ঘঢ়",

		    "sfngf" to "ষঙ", "sfngk" to "ষঙ্ক", "sfngkt" to "ষঙ্‌ক্ত", "sfngkz" to "ষঙ্ক্য", "sfngkr" to "ষঙ্ক্র", "sfngkf" to "ষঙ্ক্ষ", "sfngkkh" to "ষঙ্ক্ষ", "sfngksf" to "ষঙ্ক্ষ", "sfngkh" to "ষঙ্খ", "sfngg" to "ষঙ্গ", "sfnggz" to "ষঙ্গ্য", "sfnggh" to "ষঙ্ঘ", "sfngghz" to "ষঙ্ঘ্য", "sfngghr" to "ষঙ্ঘ্র", "sfngm" to "ষঙ্ম",
		    "sfngfk" to "ষঙ্ক", "sfngfkt" to "ষঙ্‌ক্ত", "sfngfkz" to "ষঙ্ক্য", "sfngfkr" to "ষঙ্ক্র", "sfngfkf" to "ষঙ্ক্ষ", "sfngfkkh" to "ষঙ্ক্ষ", "sfngfksf" to "ষঙ্ক্ষ", "sfngfkh" to "ষঙ্খ", "sfngfg" to "ষঙ্গ", "sfngfgz" to "ষঙ্গ্য", "sfngfgh" to "ষঙ্ঘ", "sfngfghz" to "ষঙ্ঘ্য", "sfngfghr" to "ষঙ্ঘ্র", "sfngfm" to "ষঙ্ম",
		    "sfnggg" to "ষংজ্ঞ",
		    "sfngkth" to "ষঙ্কথ", "sfngkrf" to "ষঙ্কড়", "sfngkrff" to "ষঙ্কঢ়", "sfngghrf" to "ষঙ্ঘড়", "sfngghrff" to "ষঙ্ঘঢ়",
		    "sfngfkth" to "ষঙ্কথ", "sfngfkrf" to "ষঙ্কড়", "sfngfkrff" to "ষঙ্কঢ়", "sfngfghrf" to "ষঙ্ঘড়", "sfngfghrff" to "ষঙ্ঘঢ়",

		    "sngf" to "সঙ", "sngk" to "সঙ্ক", "sngkt" to "সঙ্‌ক্ত", "sngkz" to "সঙ্ক্য", "sngkr" to "সঙ্ক্র", "sngkf" to "সঙ্ক্ষ", "sngkkh" to "সঙ্ক্ষ", "sngksf" to "সঙ্ক্ষ", "sngkh" to "সঙ্খ", "sngg" to "সঙ্গ", "snggz" to "সঙ্গ্য", "snggh" to "সঙ্ঘ", "sngghz" to "সঙ্ঘ্য", "sngghr" to "সঙ্ঘ্র", "sngm" to "সঙ্ম",
		    "sngfk" to "সঙ্ক", "sngfkt" to "সঙ্‌ক্ত", "sngfkz" to "সঙ্ক্য", "sngfkr" to "সঙ্ক্র", "sngfkf" to "সঙ্ক্ষ", "sngfkkh" to "সঙ্ক্ষ", "sngfksf" to "সঙ্ক্ষ", "sngfkh" to "সঙ্খ", "sngfg" to "সঙ্গ", "sngfgz" to "সঙ্গ্য", "sngfgh" to "সঙ্ঘ", "sngfghz" to "সঙ্ঘ্য", "sngfghr" to "সঙ্ঘ্র", "sngfm" to "সঙ্ম",
		    "snggg" to "সংজ্ঞ",
		    "sngkth" to "সঙ্কথ", "sngkrf" to "সঙ্কড়", "sngkrff" to "সঙ্কঢ়", "sngghrf" to "সঙ্ঘড়", "sngghrff" to "সঙ্ঘঢ়",
		    "sngfkth" to "সঙ্কথ", "sngfkrf" to "সঙ্কড়", "sngfkrff" to "সঙ্কঢ়", "sngfghrf" to "সঙ্ঘড়", "sngfghrff" to "সঙ্ঘঢ়",

		    "hngf" to "হঙ", "hngk" to "হঙ্ক", "hngkt" to "হঙ্‌ক্ত", "hngkz" to "হঙ্ক্য", "hngkr" to "হঙ্ক্র", "hngkf" to "হঙ্ক্ষ", "hngkkh" to "হঙ্ক্ষ", "hngksf" to "হঙ্ক্ষ", "hngkh" to "হঙ্খ", "hngg" to "হঙ্গ", "hnggz" to "হঙ্গ্য", "hnggh" to "হঙ্ঘ", "hngghz" to "হঙ্ঘ্য", "hngghr" to "হঙ্ঘ্র", "hngm" to "হঙ্ম",
		    "hngfk" to "হঙ্ক", "hngfkt" to "হঙ্‌ক্ত", "hngfkz" to "হঙ্ক্য", "hngfkr" to "হঙ্ক্র", "hngfkf" to "হঙ্ক্ষ", "hngfkkh" to "হঙ্ক্ষ", "hngfksf" to "হঙ্ক্ষ", "hngfkh" to "হঙ্খ", "hngfg" to "হঙ্গ", "hngfgz" to "হঙ্গ্য", "hngfgh" to "হঙ্ঘ", "hngfghz" to "হঙ্ঘ্য", "hngfghr" to "হঙ্ঘ্র", "hngfm" to "হঙ্ম",
		    "hnggg" to "হংজ্ঞ",
		    "hngkth" to "হঙ্কথ", "hngkrf" to "হঙ্কড়", "hngkrff" to "হঙ্কঢ়", "hngghrf" to "হঙ্ঘড়", "hngghrff" to "হঙ্ঘঢ়",
		    "hngfkth" to "হঙ্কথ", "hngfkrf" to "হঙ্কড়", "hngfkrff" to "হঙ্কঢ়", "hngfghrf" to "হঙ্ঘড়", "hngfghrff" to "হঙ্ঘঢ়",

		    "kfngf" to "ক্ষঙ", "kfngk" to "ক্ষঙ্ক", "kfngkt" to "ক্ষঙ্‌ক্ত", "kfngkz" to "ক্ষঙ্ক্য", "kfngkr" to "ক্ষঙ্ক্র", "kfngkf" to "ক্ষঙ্ক্ষ", "kfngkkh" to "ক্ষঙ্ক্ষ", "kfngksf" to "ক্ষঙ্ক্ষ", "kfngkh" to "ক্ষঙ্খ", "kfngg" to "ক্ষঙ্গ", "kfnggz" to "ক্ষঙ্গ্য", "kfnggh" to "ক্ষঙ্ঘ", "kfngghz" to "ক্ষঙ্ঘ্য", "kfngghr" to "ক্ষঙ্ঘ্র", "kfngm" to "ক্ষঙ্ম",
		    "kfngfk" to "ক্ষঙ্ক", "kfngfkt" to "ক্ষঙ্‌ক্ত", "kfngfkz" to "ক্ষঙ্ক্য", "kfngfkr" to "ক্ষঙ্ক্র", "kfngfkf" to "ক্ষঙ্ক্ষ", "kfngfkkh" to "ক্ষঙ্ক্ষ", "kfngfksf" to "ক্ষঙ্ক্ষ", "kfngfkh" to "ক্ষঙ্খ", "kfngfg" to "ক্ষঙ্গ", "kfngfgz" to "ক্ষঙ্গ্য", "kfngfgh" to "ক্ষঙ্ঘ", "kfngfghz" to "ক্ষঙ্ঘ্য", "kfngfghr" to "ক্ষঙ্ঘ্র", "kfngfm" to "ক্ষঙ্ম",
		    "kfnggg" to "ক্ষংজ্ঞ",
		    "kfngkth" to "ক্ষঙ্কথ", "kfngkrf" to "ক্ষঙ্কড়", "kfngkrff" to "ক্ষঙ্কঢ়", "kfngghrf" to "ক্ষঙ্ঘড়", "kfngghrff" to "ক্ষঙ্ঘঢ়",
	        "kfngfkth" to "ক্ষঙ্কথ", "kfngfkrf" to "ক্ষঙ্কড়", "kfngfkrff" to "ক্ষঙ্কঢ়", "kfngfghrf" to "ক্ষঙ্ঘড়", "kfngfghrff" to "ক্ষঙ্ঘঢ়",

		    "kkhngf" to "ক্ষঙ", "kkhngk" to "ক্ষঙ্ক", "kkhngkt" to "ক্ষঙ্‌ক্ত", "kkhngkz" to "ক্ষঙ্ক্য", "kkhngkr" to "ক্ষঙ্ক্র", "kkhngkf" to "ক্ষঙ্ক্ষ", "kkhngkkh" to "ক্ষঙ্ক্ষ", "kkhngksf" to "ক্ষঙ্ক্ষ", "kkhngkh" to "ক্ষঙ্খ", "kkhngg" to "ক্ষঙ্গ", "kkhnggz" to "ক্ষঙ্গ্য", "kkhnggh" to "ক্ষঙ্ঘ", "kkhngghz" to "ক্ষঙ্ঘ্য", "kkhngghr" to "ক্ষঙ্ঘ্র", "kkhngm" to "ক্ষঙ্ম",
		    "kkhngfk" to "ক্ষঙ্ক", "kkhngfkt" to "ক্ষঙ্‌ক্ত", "kkhngfkz" to "ক্ষঙ্ক্য", "kkhngfkr" to "ক্ষঙ্ক্র", "kkhngfkf" to "ক্ষঙ্ক্ষ", "kkhngfkkh" to "ক্ষঙ্ক্ষ", "kkhngfksf" to "ক্ষঙ্ক্ষ", "kkhngfkh" to "ক্ষঙ্খ", "kkhngfg" to "ক্ষঙ্গ", "kkhngfgz" to "ক্ষঙ্গ্য", "kkhngfgh" to "ক্ষঙ্ঘ", "kkhngfghz" to "ক্ষঙ্ঘ্য", "kkhngfghr" to "ক্ষঙ্ঘ্র", "kkhngfm" to "ক্ষঙ্ম",
		    "kkhnggg" to "ক্ষংজ্ঞ",
		    "kkhngkth" to "ক্ষঙ্কথ", "kkhngkrf" to "ক্ষঙ্কড়", "kkhngkrff" to "ক্ষঙ্কঢ়", "kkhngghrf" to "ক্ষঙ্ঘড়", "kkhngghrff" to "ক্ষঙ্ঘঢ়",
		    "kkhngfkth" to "ক্ষঙ্কথ", "kkhngfkrf" to "ক্ষঙ্কড়", "kkhngfkrff" to "ক্ষঙ্কঢ়", "kkhngfghrf" to "ক্ষঙ্ঘড়", "kkhngfghrff" to "ক্ষঙ্ঘঢ়",

		    "ksfngf" to "ক্ষঙ", "ksfngk" to "ক্ষঙ্ক", "ksfngkt" to "ক্ষঙ্‌ক্ত", "ksfngkz" to "ক্ষঙ্ক্য", "ksfngkr" to "ক্ষঙ্ক্র", "ksfngkf" to "ক্ষঙ্ক্ষ", "ksfngkkh" to "ক্ষঙ্ক্ষ", "ksfngksf" to "ক্ষঙ্ক্ষ", "ksfngkh" to "ক্ষঙ্খ", "ksfngg" to "ক্ষঙ্গ", "ksfnggz" to "ক্ষঙ্গ্য", "ksfnggh" to "ক্ষঙ্ঘ", "ksfngghz" to "ক্ষঙ্ঘ্য", "ksfngghr" to "ক্ষঙ্ঘ্র", "ksfngm" to "ক্ষঙ্ম",
		    "ksfngfk" to "ক্ষঙ্ক", "ksfngfkt" to "ক্ষঙ্‌ক্ত", "ksfngfkz" to "ক্ষঙ্ক্য", "ksfngfkr" to "ক্ষঙ্ক্র", "ksfngfkf" to "ক্ষঙ্ক্ষ", "ksfngfkkh" to "ক্ষঙ্ক্ষ", "ksfngfksf" to "ক্ষঙ্ক্ষ", "ksfngfkh" to "ক্ষঙ্খ", "ksfngfg" to "ক্ষঙ্গ", "ksfngfgz" to "ক্ষঙ্গ্য", "ksfngfgh" to "ক্ষঙ্ঘ", "ksfngfghz" to "ক্ষঙ্ঘ্য", "ksfngfghr" to "ক্ষঙ্ঘ্র", "ksfngfm" to "ক্ষঙ্ম",
		    "ksfnggg" to "ক্ষংজ্ঞ",
		    "ksfngkth" to "ক্ষঙ্কথ", "ksfngkrf" to "ক্ষঙ্কড়", "ksfngkrff" to "ক্ষঙ্কঢ়", "ksfngghrf" to "ক্ষঙ্ঘড়", "ksfngghrff" to "ক্ষঙ্ঘঢ়",
		    "ksfngfkth" to "ক্ষঙ্কথ", "ksfngfkrf" to "ক্ষঙ্কড়", "ksfngfkrff" to "ক্ষঙ্কঢ়", "ksfngfghrf" to "ক্ষঙ্ঘড়", "ksfngfghrff" to "ক্ষঙ্ঘঢ়"
        )

        private val REPH = mapOf(
            "rr" to "র্",
            "r" to "র"
        )

        private val PHOLA = mapOf(
            "r" to "র",
            "z" to "য"
        )

        private val KAR = mapOf(
            "o" to "", "of" to "অ",
            "oof" to "ঽ",
            "a" to "া", "af" to "আ",
            "i" to "ি", "if" to "ই",
            "ii" to "ী", "iif" to "ঈ",
            "u" to "ু", "uf" to "উ",
            "uu" to "ূ", "uuf" to "ঊ",
            "q" to "ৃ", "qf" to "ঋ",
            "e" to "ে", "ef" to "এ",
            "oi" to "ৈ", "oif" to "ই",
            "w" to "ো", "wf" to "ও",
            "ou" to "ৌ", "ouf" to "উ",
            "ae" to "্যা", "aef" to "অ্যা",
            "uff" to "‌ু", "uuff" to "‌ূ", "qff" to "‌ৃ",
            "we" to "োয়ে", "wef" to "ওয়ে",
            "waf" to "ওয়া", "wa" to "োয়া",
            "wae" to "ওয়্যা",
            "oo" to "ং"
        )

        private val DIACRITIC = mapOf(
            "qq" to "্", "xx" to "্‌", "t/" to "ৎ", "x" to "ঃ", "/" to "ঁ", "//" to "/"
        )

        private val BIRAM = mapOf(
            "।ff" to "৺"
        )

        private val PRITHAYOK = mapOf(
            ";" to "", ";;" to ";"
        )

        private val AE = mapOf(
            "ae" to "‍্যা"
        )

        // Ng Group
        private val NG = mapOf(
            // Base ং forms
            "ng" to "ং", "gng" to "গং", "ghng" to "ঘং", "cng" to "চং", "jng" to "জং", "nng" to "নং", "nfng" to "ণং", "tng" to "তং", "dhng" to "ধং", "png" to "পং", "mng" to "মং", "shng" to "শং", "sfng" to "ষং", "sng" to "সং", "hng" to "হং", "kfng" to "ক্ষং", "kkhng" to "ক্ষং", "ksfng" to "ক্ষং",
            
            "ngo" to "ঙ", "nga" to "ঙা", "ngi" to "ঙি", "ngii" to "ঙী", "ngu" to "ঙু",
            "nguff" to "ঙ‌ু", "nguu" to "ঙূ", "nguuff" to "ঙ‌ূ", "ngq" to "ঙৃ", "ngqff" to "ঙ‌ৃ",
            "nge" to "ঙে", "ngoi" to "ঙৈ", "ngw" to "ঙো", "ngou" to "ঙৌ", "ngae" to "ঙ্যা",
            "ngof" to "ঙঅ", "ngaf" to "ঙআ", "ngif" to "ঙই", "ngiif" to "ঙঈ",
            "nguf" to "ঙউ", "nguuf" to "ঙঊ", "ngqf" to "ঙঋ", "ngef" to "ঙএ",
            "ngoif" to "ঙই", "ngwf" to "ঙও", "ngouf" to "ঙউ", "ngaef" to "ঙঅ্যা",
            
		    "gngo" to "গঙ", "gnga" to "গঙা", "gngi" to "গঙি", "gngii" to "গঙী", "gngu" to "গঙু", "gnguff" to "গঙ‌ু", "gnguu" to "গঙূ", "gnguuff" to "গঙ‌ূ", "gngq" to "গঙৃ", "gngqff" to "গঙ‌ৃ", "gnge" to "গঙে", "gngoi" to "গঙৈ",     
		    "gngw" to "গঙো", "gngou" to "গঙৌ", "gngae" to "গঙ্যা",
		    "gngof" to "গঙঅ", "gngaf" to "গঙআ", "gngif" to "গঙই", "gngiif" to "গঙঈ", "gnguf" to "গঙউ", "gnguuf" to "গঙঊ", "gngqf" to "গঙঋ", "gngef" to "গঙএ", "gngoif" to "গঙই",     
		    "gngwf" to "গঙও", "gngouf" to "গঙউ", "gngaef" to "গঙঅ্যা",

		    "ghngo" to "ঘঙ", "ghnga" to "ঘঙা", "ghngi" to "ঘঙি", "ghngii" to "ঘঙী", "ghngu" to "ঘঙু", "ghnguff" to "ঘঙ‌ু", "ghnguu" to "ঘঙূ", "ghnguuff" to "ঘঙ‌ূ", "ghngq" to "ঘঙৃ", "ghngqff" to "ঘঙ‌ৃ", "ghnge" to "ঘঙে", "ghngoi" to "ঘঙৈ",     
		    "ghngw" to "ঘঙো", "ghngou" to "ঘঙৌ", "ghngae" to "ঘঙ্যা",
		    "ghngof" to "ঘঙঅ", "ghngaf" to "ঘঙআ", "ghngif" to "ঘঙই", "ghngiif" to "ঘঙঈ", "ghnguf" to "ঘঙউ", "ghnguuf" to "ঘঙঊ", "ghngqf" to "ঘঙঋ", "ghngef" to "ঘঙএ", "ghngoif" to "ঘঙই",     
		    "ghngwf" to "ঘঙও", "ghngouf" to "ঘঙউ", "ghngaef" to "ঘঙঅ্যা",

		    "cngo" to "চঙ", "cnga" to "চঙা", "cngi" to "চঙি", "cngii" to "চঙী", "cngu" to "চঙু", "cnguff" to "চঙ‌ু", "cnguu" to "চঙূ", "cnguuff" to "চঙ‌ূ", "cngq" to "চঙৃ", "cngqff" to "চঙ‌ৃ", "cnge" to "চঙে", "cngoi" to "চঙৈ",     
		    "cngw" to "চঙো", "cngou" to "চঙৌ", "cngae" to "চঙ্যা",
		    "cngof" to "চঙঅ", "cngaf" to "চঙআ", "cngif" to "চঙই", "cngiif" to "চঙঈ", "cnguf" to "চঙউ", "cnguuf" to "চঙঊ", "cngqf" to "চঙঋ", "cngef" to "চঙএ", "cngoif" to "চঙই",     
		    "cngwf" to "চঙও", "cngouf" to "চঙউ", "cngaef" to "চঙঅ্যা",

		    "jngo" to "জঙ", "jnga" to "জঙা", "jngi" to "জঙি", "jngii" to "জঙী", "jngu" to "জঙু", "jnguff" to "জঙ‌ু", "jnguu" to "জঙূ", "jnguuff" to "জঙ‌ূ", "jngq" to "জঙৃ", "jngqff" to "জঙ‌ৃ", "jnge" to "জঙে", "jngoi" to "জঙৈ",     
		    "jngw" to "জঙো", "jngou" to "জঙৌ", "jngae" to "জঙ্যা",
		    "jngof" to "জঙঅ", "jngaf" to "জঙআ", "jngif" to "জঙই", "jngiif" to "জঙঈ", "jnguf" to "জঙউ", "jnguuf" to "জঙঊ", "jngqf" to "জঙঋ", "jngef" to "জঙএ", "jngoif" to "জঙই",     
		    "jngwf" to "জঙও", "jngouf" to "জঙউ", "jngaef" to "জঙঅ্যা",

		    "nngo" to "নঙ", "nnga" to "নঙা", "nngi" to "নঙি", "nngii" to "নঙী", "nngu" to "নঙু", "nnguff" to "নঙ‌ু", "nnguu" to "নঙূ", "nnguuff" to "নঙ‌ূ", "nngq" to "নঙৃ", "nngqff" to "নঙ‌ৃ", "nnge" to "নঙে", "nngoi" to "নঙৈ",     
		    "nngw" to "নঙো", "nngou" to "নঙৌ", "nngae" to "নঙ্যা",
		    "nngof" to "নঙঅ", "nngaf" to "নঙআ", "nngif" to "নঙই", "nngiif" to "নঙঈ", "nnguf" to "নঙউ", "nnguuf" to "নঙঊ", "nngqf" to "নঙঋ", "nngef" to "নঙএ", "nngoif" to "নঙই",     
		    "nngwf" to "নঙও", "nngouf" to "নঙউ", "nngaef" to "নঙঅ্যা",

		    "nfngo" to "ণঙ", "nfnga" to "ণঙা", "nfngi" to "ণঙি", "nfngii" to "ণঙী", "nfngu" to "ণঙু", "nfnguff" to "ণঙ‌ু", "nfnguu" to "ণঙূ", "nfnguuff" to "ণঙ‌ূ", "nfngq" to "ণঙৃ", "nfngqff" to "ণঙ‌ৃ", "nfnge" to "ণঙে", "nfngoi" to "ণঙৈ",     
		    "nfngw" to "ণঙো", "nfngou" to "ণঙৌ", "nfngae" to "ণঙ্যা",
		    "nfngof" to "ণঙঅ", "nfngaf" to "ণঙআ", "nfngif" to "ণঙই", "nfngiif" to "ণঙঈ", "nfnguf" to "ণঙউ", "nfnguuf" to "ণঙঊ", "nfngqf" to "ণঙঋ", "nfngef" to "ণঙএ", "nfngoif" to "ণঙই",     
		    "nfngwf" to "ণঙও", "nfngouf" to "ণঙউ", "nfngaef" to "ণঙঅ্যা",

		    "tngo" to "তঙ", "tnga" to "তঙা", "tngi" to "তঙি", "tngii" to "তঙী", "tngu" to "তঙু", "tnguff" to "তঙ‌ু", "tnguu" to "তঙূ", "tnguuff" to "তঙ‌ূ", "tngq" to "তঙৃ", "tngqff" to "তঙ‌ৃ", "tnge" to "তঙে", "tngoi" to "তঙৈ",     
		    "tngw" to "তঙো", "tngou" to "তঙৌ", "tngae" to "তঙ্যা",
		    "tngof" to "তঙঅ", "tngaf" to "তঙআ", "tngif" to "তঙই", "tngiif" to "তঙঈ", "tnguf" to "তঙউ", "tnguuf" to "তঙঊ", "tngqf" to "তঙঋ", "tngef" to "তঙএ", "tngoif" to "তঙই",     
		    "tngwf" to "তঙও", "tngouf" to "তঙউ", "tngaef" to "তঙঅ্যা",

		    "dhngo" to "ধঙ", "dhnga" to "ধঙা", "dhngi" to "ধঙি", "dhngii" to "ধঙী", "dhngu" to "ধঙু", "dhnguff" to "ধঙ‌ু", "dhnguu" to "ধঙূ", "dhnguuff" to "ধঙ‌ূ", "dhngq" to "ধঙৃ", "dhngqff" to "ধঙ‌ৃ", "dhnge" to "ধঙে", "dhngoi" to "ধঙৈ",     
		    "dhngw" to "ধঙো", "dhngou" to "ধঙৌ", "dhngae" to "ধঙ্যা",
		    "dhngof" to "ধঙঅ", "dhngaf" to "ধঙআ", "dhngif" to "ধঙই", "dhngiif" to "ধঙঈ", "dhnguf" to "ধঙউ", "dhnguuf" to "ধঙঊ", "dhngqf" to "ধঙঋ", "dhngef" to "ধঙএ", "dhngoif" to "ধঙই",     
		    "dhngwf" to "ধঙও", "dhngouf" to "ধঙউ", "dhngaef" to "ধঙঅ্যা",

		    "pngo" to "পঙ", "pnga" to "পঙা", "pngi" to "পঙি", "pngii" to "পঙী", "pngu" to "পঙু", "pnguff" to "পঙ‌ু", "pnguu" to "পঙূ", "pnguuff" to "পঙ‌ূ", "pngq" to "পঙৃ", "pngqff" to "পঙ‌ৃ", "pnge" to "পঙে", "pngoi" to "পঙৈ",     
		    "pngw" to "পঙো", "pngou" to "পঙৌ", "pngae" to "পঙ্যা",
		    "pngof" to "পঙঅ", "pngaf" to "পঙআ", "pngif" to "পঙই", "pngiif" to "পঙঈ", "pnguf" to "পঙউ", "pnguuf" to "পঙঊ", "pngqf" to "পঙঋ", "pngef" to "পঙএ", "pngoif" to "পঙই",     
		    "pngwf" to "পঙও", "pngouf" to "পঙউ", "pngaef" to "পঙঅ্যা",

		    "mngo" to "মঙ", "mnga" to "মঙা", "mngi" to "মঙি", "mngii" to "মঙী", "mngu" to "মঙু", "mnguff" to "মঙ‌ু", "mnguu" to "মঙূ", "mnguuff" to "মঙ‌ূ", "mngq" to "মঙৃ", "mngqff" to "মঙ‌ৃ", "mnge" to "মঙে", "mngoi" to "মঙৈ",     
		    "mngw" to "মঙো", "mngou" to "মঙৌ", "mngae" to "মঙ্যা",
		    "mngof" to "মঙঅ", "mngaf" to "মঙআ", "mngif" to "মঙই", "mngiif" to "মঙঈ", "mnguf" to "মঙউ", "mnguuf" to "মঙঊ", "mngqf" to "মঙঋ", "mngef" to "মঙএ", "mngoif" to "মঙই",     
		    "mngwf" to "মঙও", "mngouf" to "মঙউ", "mngaef" to "মঙঅ্যা",

		    "shngo" to "শঙ", "shnga" to "শঙা", "shngi" to "শঙি", "shngii" to "শঙী", "shngu" to "শঙু", "shnguff" to "শঙ‌ু", "shnguu" to "শঙূ", "shnguuff" to "শঙ‌ূ", "shngq" to "শঙৃ", "shngqff" to "শঙ‌ৃ", "shnge" to "শঙে", "shngoi" to "শঙৈ",     
		    "shngw" to "শঙো", "shngou" to "শঙৌ", "shngae" to "শঙ্যা",
		    "shngof" to "শঙঅ", "shngaf" to "শঙআ", "shngif" to "শঙই", "shngiif" to "শঙঈ", "shnguf" to "শঙউ", "shnguuf" to "শঙঊ", "shngqf" to "শঙঋ", "shngef" to "শঙএ", "shngoif" to "শঙই",     
		    "shngwf" to "শঙও", "shngouf" to "শঙউ", "shngaef" to "শঙঅ্যা",

		    "sfngo" to "ষঙ", "sfnga" to "ষঙা", "sfngi" to "ষঙি", "sfngii" to "ষঙী", "sfngu" to "ষঙু", "sfnguff" to "ষঙ‌ু", "sfnguu" to "ষঙূ", "sfnguuff" to "ষঙ‌ূ", "sfngq" to "ষঙৃ", "sfngqff" to "ষঙ‌ৃ", "sfnge" to "ষঙে", "sfngoi" to "ষঙৈ",     
		    "sfngw" to "ষঙো", "sfngou" to "ষঙৌ", "sfngae" to "ষঙ্যা",
		    "sfngof" to "ষঙঅ", "sfngaf" to "ষঙআ", "sfngif" to "ষঙই", "sfngiif" to "ষঙঈ", "sfnguf" to "ষঙউ", "sfnguuf" to "ষঙঊ", "sfngqf" to "ষঙঋ", "sfngef" to "ষঙএ", "sfngoif" to "ষঙই",     
		    "sfngwf" to "ষঙও", "sfngouf" to "ষঙউ", "sfngaef" to "ষঙঅ্যা",

		    "sngo" to "সঙ", "snga" to "সঙা", "sngi" to "সঙি", "sngii" to "সঙী", "sngu" to "সঙু", "snguff" to "সঙ‌ু", "snguu" to "সঙূ", "snguuff" to "সঙ‌ূ", "sngq" to "সঙৃ", "sngqff" to "সঙ‌ৃ", "snge" to "সঙে", "sngoi" to "সঙৈ",     
		    "sngw" to "সঙো", "sngou" to "সঙৌ", "sngae" to "সঙ্যা",
		    "sngof" to "সঙঅ", "sngaf" to "সঙআ", "sngif" to "সঙই", "sngiif" to "সঙঈ", "snguf" to "সঙউ", "snguuf" to "সঙঊ", "sngqf" to "সঙঋ", "sngef" to "সঙএ", "sngoif" to "সঙই",     
		    "sngwf" to "সঙও", "sngouf" to "সঙউ", "sngaef" to "সঙঅ্যা",

		    "hngo" to "হঙ", "hnga" to "হঙা", "hngi" to "হঙি", "hngii" to "হঙী", "hngu" to "হঙু", "hnguff" to "হঙ‌ু", "hnguu" to "হঙূ", "hnguuff" to "হঙ‌ূ", "hngq" to "হঙৃ", "hngqff" to "হঙ‌ৃ", "hnge" to "হঙে", "hngoi" to "হঙৈ",     
		    "hngw" to "হঙো", "hngou" to "হঙৌ", "hngae" to "হঙ্যা",
		    "hngof" to "হঙঅ", "hngaf" to "হঙআ", "hngif" to "হঙই", "hngiif" to "হঙঈ", "hnguf" to "হঙউ", "hnguuf" to "হঙঊ", "hngqf" to "হঙঋ", "hngef" to "হঙএ", "hngoif" to "হঙই",     
		    "hngwf" to "হঙও", "hngouf" to "হঙউ", "hngaef" to "হঙঅ্যা",

		    "kfngo" to "ক্ষঙ", "kfnga" to "ক্ষঙা", "kfngi" to "ক্ষঙি", "kfngii" to "ক্ষঙী", "kfngu" to "ক্ষঙু", "kfnguff" to "ক্ষঙ‌ু", "kfnguu" to "ক্ষঙূ", "kfnguuff" to "ক্ষঙ‌ূ", "kfngq" to "ক্ষঙৃ", "kfngqff" to "ক্ষঙ‌ৃ", "kfnge" to "ক্ষঙে", "kfngoi" to "ক্ষঙৈ",     
		    "kfngw" to "ক্ষঙো", "kfngou" to "ক্ষঙৌ", "kfngae" to "ক্ষঙ্যা",
		    "kfngof" to "ক্ষঙঅ", "kfngaf" to "ক্ষঙআ", "kfngif" to "ক্ষঙই", "kfngiif" to "ক্ষঙঈ", "kfnguf" to "ক্ষঙউ", "kfnguuf" to "ক্ষঙঊ", "kfngqf" to "ক্ষঙঋ", "kfngef" to "ক্ষঙএ", "kfngoif" to "ক্ষঙই",     
		    "kfngwf" to "ক্ষঙও", "kfngouf" to "ক্ষঙউ", "kfngaef" to "ক্ষঙঅ্যা",

		    "kkhngo" to "ক্ষঙ", "kkhnga" to "ক্ষঙা", "kkhngi" to "ক্ষঙি", "kkhngii" to "ক্ষঙী", "kkhngu" to "ক্ষঙু", "kkhnguff" to "ক্ষঙ‌ু", "kkhnguu" to "ক্ষঙূ", "kkhnguuff" to "ক্ষঙ‌ূ", "kkhngq" to "ক্ষঙৃ", "kkhngqff" to "ক্ষঙ‌ৃ", "kkhnge" to "ক্ষঙে", "kkhngoi" to "ক্ষঙৈ",     
		    "kkhngw" to "ক্ষঙো", "kkhngou" to "ক্ষঙৌ", "kkhngae" to "ক্ষঙ্যা",
		    "kkhngof" to "ক্ষঙঅ", "kkhngaf" to "ক্ষঙআ", "kkhngif" to "ক্ষঙই", "kkhngiif" to "ক্ষঙঈ", "kkhnguf" to "ক্ষঙউ", "kkhnguuf" to "ক্ষঙঊ", "kkhngqf" to "ক্ষঙঋ", "kkhngef" to "ক্ষঙএ", "kkhngoif" to "ক্ষঙই",     
		    "kkhngwf" to "ক্ষঙও", "kkhngouf" to "ক্ষঙউ", "kkhngaef" to "ক্ষঙঅ্যা",

		    "ksfngo" to "ক্ষঙ", "ksfnga" to "ক্ষঙা", "ksfngi" to "ক্ষঙি", "ksfngii" to "ক্ষঙী", "ksfngu" to "ক্ষঙু", "ksfnguff" to "ক্ষঙ‌ু", "ksfnguu" to "ক্ষঙূ", "ksfnguuff" to "ক্ষঙ‌ূ", "ksfngq" to "ক্ষঙৃ", "ksfngqff" to "ক্ষঙ‌ৃ", "ksfnge" to "ক্ষঙে", "ksfngoi" to "ক্ষঙৈ",     
		    "ksfngw" to "ক্ষঙো", "ksfngou" to "ক্ষঙৌ", "ksfngae" to "ক্ষঙ্যা",
		    "ksfngof" to "ক্ষঙঅ", "ksfngaf" to "ক্ষঙআ", "ksfngif" to "ক্ষঙই", "ksfngiif" to "ক্ষঙঈ", "ksfnguf" to "ক্ষঙউ", "ksfnguuf" to "ক্ষঙঊ", "ksfngqf" to "ক্ষঙঋ", "ksfngef" to "ক্ষঙএ", "ksfngoif" to "ক্ষঙই",     
		    "ksfngwf" to "ক্ষঙও", "ksfngouf" to "ক্ষঙউ", "ksfngaef" to "ক্ষঙঅ্যা"
        )

        private val GROUP_MAPS = mapOf(
            "shor" to SHOR,
            "fkar" to FKAR,
            "byanjon" to BYANJON,
            "juktoborno" to JUKTOBORNO,
            "reph" to REPH,
            "phola" to PHOLA,
            "kar" to KAR,
            "diacritic" to DIACRITIC,
            "biram" to BIRAM,
            "prithayok" to PRITHAYOK,
            "ae" to AE,
            "ng" to NG
        )

        // State Priorities
        private val STATE_GROUP_ORDER = mapOf(
            State.INIT to listOf("diacritic", "ng", "shor", "fkar", "prithayok", "biram", "reph", "byanjon", "juktoborno"),
            State.SHOR_STATE to listOf("diacritic", "ng", "shor", "fkar", "biram", "prithayok", "reph", "byanjon", "juktoborno"),
            State.REPH_STATE to listOf("prithayok", "ae", "byanjon", "juktoborno", "ng", "kar"),
            State.BYANJON_STATE to listOf("diacritic", "ng", "prithayok", "biram", "kar", "phola", "byanjon", "juktoborno")
        )

        private val MAXLEN_PER_GROUP = GROUP_MAPS.mapValues { (_, map) ->
            map.keys.maxOfOrNull { it.length } ?: 0
        }

        private fun findLongest(state: State, text: String, i: Int): Triple<String, String, String> {
            val allowed = STATE_GROUP_ORDER[state] ?: return Triple("", "", "")

            val maxlen = allowed.maxOfOrNull { MAXLEN_PER_GROUP[it] ?: 0 } ?: 0
            val end = minOf(text.length, i + maxlen)

            for (l in (end - i) downTo 1) {
                val chunk = text.substring(i, i + l)
                for (g in allowed) {
                    val map = GROUP_MAPS[g]
                    if (map?.containsKey(chunk) == true) {
                        return Triple(g, chunk, map[chunk]!!)
                    }
                }
            }
            return Triple("", "", "")
        }

        private fun applyTransition(state: State, group: String): State {
            return when (state) {
                State.INIT -> when (group) {
                    "diacritic", "shor", "fkar" -> State.SHOR_STATE
                    "prithayok" -> State.SHOR_STATE
                    "biram" -> State.SHOR_STATE
                    "reph" -> State.REPH_STATE
                    "byanjon" -> State.BYANJON_STATE
                    "juktoborno" -> State.BYANJON_STATE
                    "ng" -> State.SHOR_STATE
                    else -> state
                }
                State.SHOR_STATE -> when (group) {
                    "diacritic", "shor", "fkar" -> State.SHOR_STATE
                    "biram" -> State.SHOR_STATE
                    "prithayok" -> State.SHOR_STATE
                    "reph" -> State.REPH_STATE
                    "byanjon" -> State.BYANJON_STATE
                    "juktoborno" -> State.BYANJON_STATE
                    "ng" -> State.SHOR_STATE
                    else -> state
                }
                State.REPH_STATE -> when (group) {
                    "diacritic" -> State.SHOR_STATE
                    "prithayok" -> State.SHOR_STATE
                    "ae" -> State.SHOR_STATE
                    "byanjon" -> State.BYANJON_STATE
                    "juktoborno" -> State.BYANJON_STATE
                    "kar" -> State.SHOR_STATE
                    "ng" -> State.SHOR_STATE
                    else -> state
                }
                State.BYANJON_STATE -> when (group) {
                    "diacritic", "kar" -> State.SHOR_STATE
                    "prithayok" -> State.SHOR_STATE
                    "biram" -> State.SHOR_STATE
                    "byanjon" -> State.BYANJON_STATE
                    "juktoborno" -> State.BYANJON_STATE
                    "ng" -> State.SHOR_STATE
                    else -> state
                }
            }
        }

        /**
         * Convert an ASCII input string to Bengali output using the bn-khipro state machine.
         */
        fun convert(text: String): String {
            var i = 0
            val n = text.length
            var state = State.INIT
            val out = mutableListOf<String>()

            while (i < n) {
                val (group, key, value) = findLongest(state, text, i)
                if (group.isEmpty()) {
                    out.add(text[i].toString())
                    i += 1
                    state = State.INIT
                    continue
                }

                if (state == State.BYANJON_STATE && group == "phola") {
                    out.add("্")
                    out.add(value)
                } else {
                    out.add(value)
                }

                i += key.length
                state = applyTransition(state, group)
            }

            return out.joinToString("")
        }
    }

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        if (event.keyCode == KeyCode.SHIFT) return event

        if (Character.isWhitespace(event.codePoint)) {
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        } else if (event.isFunctionalKeyEvent) {
            if (event.keyCode == KeyCode.DELETE) {
                val text = combiningStateFeedback
                reset()
                return createEventChainFromSequence(text, event)
            }
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        } else {
            composingText.append(Character.toChars(event.codePoint))

            val text = composingText.toString()
            if (text.endsWith(".ff")) {
                val result = combiningStateFeedback
                reset()
                return createEventChainFromSequence(result, event)
            }

            return Event.createConsumedEvent(event)
        }
    }

    override val combiningStateFeedback: CharSequence
        get() = convert(composingText.toString())

    override fun reset() {
        composingText.setLength(0)
    }

    private fun createEventChainFromSequence(text: CharSequence, originalEvent: Event): Event {
        return Event.createSoftwareTextEvent(text, KeyCode.MULTIPLE_CODE_POINTS, originalEvent)
    }
}