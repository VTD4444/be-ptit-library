package vn.edu.ptit.library.search.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tien ich xu ly cau truy van va tao trich doan co highlight cho tim kiem noi dung.
 * Tach tu theo am tiet (khop voi to_tsvector('simple', ...) ma Python Worker dung khi danh chi muc).
 */
public final class SearchTextUtils {

    private static final int MAX_TERMS = 32;
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    // Hu tu tieng Viet pho bien: bo khoi phan keyword de tranh khop tran lan khi truy van la ca doan van
    private static final Set<String> STOPWORDS = Set.of(
            "và", "là", "của", "có", "các", "những", "được", "trong", "cho", "với", "một", "này", "đó",
            "để", "không", "thì", "ở", "về", "khi", "theo", "từ", "như", "đến", "cũng", "nhưng", "hay",
            "hoặc", "nào", "gì", "bị", "do", "vì", "nên", "mà", "ra", "vào", "lại", "đã", "đang", "sẽ",
            "the", "a", "an", "of", "and", "or", "to", "in", "on", "for", "is", "are", "with", "by");

    private SearchTextUtils() {
    }

    public static String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFC).trim();
    }

    /**
     * Danh sach tu khoa (chu thuong, khong trung lap) dung cho tsquery va highlight.
     */
    public static List<String> extractTerms(String query) {
        Set<String> all = new LinkedHashSet<>();
        for (String token : TOKEN_SPLIT.split(normalize(query).toLowerCase(Locale.ROOT))) {
            if (!token.isBlank()) {
                all.add(token);
            }
        }
        List<String> meaningful = all.stream().filter(t -> !STOPWORDS.contains(t)).toList();
        List<String> terms = meaningful.isEmpty() ? new ArrayList<>(all) : meaningful;
        return terms.size() > MAX_TERMS ? terms.subList(0, MAX_TERMS) : terms;
    }

    /**
     * Ghep tu khoa bang toan tu OR: truy van co the la ca doan van, AND se gan nhu khong khop.
     * ts_rank_cd van uu tien doan chua nhieu tu khoa va dung gan nhau hon.
     * Token chi gom chu/so nen an toan khi dua vao to_tsquery.
     */
    public static String toTsQuery(List<String> terms) {
        return terms.isEmpty() ? null : String.join(" | ", terms);
    }

    /**
     * Trich doan ~windowSize ky tu chua nhieu tu khoa nhat; noi dung duoc escape HTML truoc khi boc &lt;mark&gt;.
     */
    public static String buildSnippet(String text, List<String> terms, int windowSize) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String source = normalize(text).replaceAll("\\s+", " ");
        if (terms.isEmpty()) {
            return escapeHtml(truncate(source, 0, windowSize));
        }

        Pattern pattern = termPattern(terms);
        List<int[]> matches = new ArrayList<>();
        Matcher m = pattern.matcher(source);
        while (m.find()) {
            matches.add(new int[]{m.start(), m.end()});
        }
        if (matches.isEmpty()) {
            return escapeHtml(truncate(source, 0, windowSize));
        }

        // Chon cua so chua nhieu lan khop nhat
        int bestStart = matches.get(0)[0];
        int bestCount = 0;
        for (int i = 0; i < matches.size(); i++) {
            int count = 0;
            for (int j = i; j < matches.size() && matches.get(j)[1] <= matches.get(i)[0] + windowSize; j++) {
                count++;
            }
            if (count > bestCount) {
                bestCount = count;
                bestStart = matches.get(i)[0];
            }
        }

        int start = Math.max(0, bestStart - windowSize / 5);
        if (start > 0) {
            int space = source.indexOf(' ', start);
            start = space >= 0 && space < bestStart ? space + 1 : start;
        }
        int end = Math.min(source.length(), start + windowSize);
        if (end < source.length()) {
            int space = source.lastIndexOf(' ', end);
            end = space > start ? space : end;
        }

        StringBuilder sb = new StringBuilder();
        if (start > 0) {
            sb.append("… ");
        }
        int cursor = start;
        for (int[] match : matches) {
            if (match[0] < start || match[1] > end) {
                continue;
            }
            sb.append(escapeHtml(source.substring(cursor, match[0])))
                    .append("<mark>")
                    .append(escapeHtml(source.substring(match[0], match[1])))
                    .append("</mark>");
            cursor = match[1];
        }
        sb.append(escapeHtml(source.substring(cursor, end)));
        if (end < source.length()) {
            sb.append(" …");
        }
        return sb.toString();
    }

    private static Pattern termPattern(List<String> terms) {
        String alternatives = terms.stream()
                .sorted((a, b) -> b.length() - a.length())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String truncate(String text, int start, int length) {
        if (text.length() <= start + length) {
            return text.substring(start);
        }
        int end = text.lastIndexOf(' ', start + length);
        return text.substring(start, end > start ? end : start + length) + " …";
    }

    private static String escapeHtml(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }
}
