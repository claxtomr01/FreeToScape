package com.freetoscape;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class EventFormat {
    private static final Set<String> BOSSES = new HashSet<>(Arrays.asList("abyssal_sire", "alchemical_hydra", "amoxliatl", "araxxor", "artio", "barrows_chests", "brutus", "bryophyta", "callisto", "calvarion", "cerberus", "chambers_of_xeric", "chambers_of_xeric_challenge_mode", "chaos_elemental", "chaos_fanatic", "commander_zilyana", "corporeal_beast", "crazy_archaeologist", "dagannoth_prime", "dagannoth_rex", "dagannoth_supreme", "deranged_archaeologist", "duke_sucellus", "general_graardor", "giant_mole", "grotesque_guardians", "hespori", "kalphite_queen", "king_black_dragon", "kraken", "kreearra", "kril_tsutsaroth", "lunar_chests", "mad_angel", "mimic", "nex", "nightmare", "phosanis_nightmare", "obor", "phantom_muspah", "sarachnis", "scorpia", "scurrius", "scotizo", "sol_heredit", "spindel", "tempoross", "the_gauntlet", "the_corrupted_gauntlet", "the_hueycoatl", "the_leviathan", "the_royal_titans", "the_whisperer", "theatre_of_blood", "theatre_of_blood_hard_mode", "thermonuclear_smoke_devil", "tombs_of_amascut", "tombs_of_amascut_expert", "tzkal_zuk", "tztok_jad", "vardorvis", "venenatis", "vetion", "vorkath", "wintertodt", "yama", "zalcano", "zulrah"));
    private static final Set<String> SKILLS = new HashSet<>(Arrays.asList("overall", "attack", "defence", "strength", "hitpoints", "ranged", "prayer", "magic", "cooking", "woodcutting", "fletching", "fishing", "firemaking", "crafting", "smithing", "mining", "herblore", "agility", "thieving", "slayer", "farming", "runecrafting", "hunter", "construction", "sailing"));
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z", Locale.UK)
        .withZone(ZoneId.of("Europe/London"));
    static boolean boss(String metric) { return BOSSES.contains(metric); }
    static boolean skill(String metric) { return SKILLS.contains(metric); }
    static String name(String metric) {
        if (metric.equals("ehb")) return "Efficient Hours Bossed";
        if (metric.equals("ehp")) return "Efficient Hours Played";
        if (metric.equals("overall")) return "Overall XP";
        StringBuilder text = new StringBuilder();
        for (String word : metric.split("_")) {
            if (word.isEmpty()) continue;
            if (text.length() > 0) text.append(' ');
            text.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return text.toString();
    }
    static String unit(String metric) {
        if (skill(metric)) return "XP";
        if (metric.endsWith("_chests")) return "chests";
        if (Arrays.asList("chambers_of_xeric", "chambers_of_xeric_challenge_mode", "theatre_of_blood",
                "theatre_of_blood_hard_mode", "tombs_of_amascut", "tombs_of_amascut_expert",
                "the_gauntlet", "the_corrupted_gauntlet").contains(metric)) return "completions";
        if (boss(metric)) return "kills";
        if (metric.equals("ehb") || metric.equals("ehp")) return "hours";
        if (metric.startsWith("clue_scrolls")) return "clues";
        if (metric.endsWith("points") || metric.endsWith("score")) return "points";
        return "gained";
    }
    static String amount(double value, String metric) {
        return String.format(Locale.UK, unit(metric).equals("hours") ? "%,.2f" : "%,.0f", value)
            + " " + unit(metric);
    }
    static String date(Instant value) { return DATE.format(value); }
    private static String span(long seconds) {
        if (seconds < 60) return "less than a minute";
        long days = seconds / 86400, hours = seconds % 86400 / 3600, minutes = seconds % 3600 / 60;
        if (days > 0) return days + (days == 1 ? " day" : " days")
            + (hours > 0 ? " " + hours + (hours == 1 ? " hour" : " hours") : "");
        if (hours > 0) return hours + (hours == 1 ? " hour" : " hours")
            + (minutes > 0 ? " " + minutes + (minutes == 1 ? " minute" : " minutes") : "");
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
    static String timing(Instant start, Instant end, Instant now) {
        if (now.isBefore(start)) return "Starts in " + span(Duration.between(now, start).getSeconds());
        if (now.isBefore(end)) return "Ends in " + span(Duration.between(now, end).getSeconds());
        return "Ended " + span(Duration.between(end, now).getSeconds()) + " ago";
    }
}
