package vn.edu.ptit.library.user.util;

import vn.edu.ptit.library.common.enums.UserType;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PtitEmailParser {

    // Mau ma sinh vien PTIT trong email:
    // - Rut gon (mail @stu.ptit.edu.vn): b22pt058, b21cn001 -> bo sung "DC" -> B22DCPT058, B21DCCN001
    // - Day du: b21dccn001, n20dcat015 -> giu nguyen
    private static final Pattern STUDENT_CODE_PATTERN = Pattern.compile(
            "(?:^|[._-])(([BCN])(\\d{2})([A-Z]{2,6})(\\d{2,4}))(?:$|[._-])",
            Pattern.CASE_INSENSITIVE
    );

    // Cat ma lop / ma sinh vien PTIT o cuoi ho ten tu Microsoft (VD: "Dinh Vu Trung Duc D22PT02", "Nguyen Van An D21CQCN01-B")
    private static final Pattern TRAILING_PTIT_CLASS_OR_CODE = Pattern.compile(
            "(?:\\s*[-–|]\\s*|\\s+)\\(?[A-Z]\\d{2}[A-Z]{2,6}\\d{1,4}(?:-[A-Z0-9]{1,3})?\\)?\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]+");

    private PtitEmailParser() {
    }

    public record ParsedPtitEmail(
            String userCode,
            Integer enrollmentYear,
            String userType
    ) {
    }

    public static ParsedPtitEmail parse(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        int atIndex = normalized.lastIndexOf('@');
        String localPart = atIndex > 0 ? normalized.substring(0, atIndex) : normalized;
        String domain = atIndex > 0 && atIndex < normalized.length() - 1
                ? normalized.substring(atIndex + 1)
                : "";

        boolean isStudentDomain = "stu.ptit.edu.vn".equals(domain) || domain.endsWith(".stu.ptit.edu.vn");
        String userType = isStudentDomain ? UserType.STUDENT.name() : UserType.LECTURER.name();

        Matcher matcher = STUDENT_CODE_PATTERN.matcher(localPart);
        if (matcher.find()) {
            String campusPrefix = matcher.group(2).toUpperCase(Locale.ROOT);
            String yearPart = matcher.group(3);
            String majorPart = matcher.group(4).toUpperCase(Locale.ROOT);
            String seqPart = matcher.group(5);
            if (majorPart.length() == 2 && !"DC".equals(majorPart)) {
                majorPart = "DC" + majorPart;
            }
            String studentCode = campusPrefix + yearPart + majorPart + seqPart;
            int yearTwoDigits = Integer.parseInt(yearPart);
            Integer enrollmentYear = 2000 + yearTwoDigits;
            return new ParsedPtitEmail(studentCode, enrollmentYear, UserType.STUDENT.name());
        }

        String sanitized = NON_ALPHANUMERIC.matcher(localPart.toUpperCase(Locale.ROOT))
                .replaceAll("_")
                .replaceAll("^_+|_+$", "");
        if (sanitized.isEmpty()) {
            sanitized = "PTIT_USER";
        }
        if (sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32);
        }
        return new ParsedPtitEmail(sanitized, null, userType);
    }

    public static String cleanFullName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String normalized = MULTIPLE_SPACES.matcher(rawName.trim()).replaceAll(" ");
        if (normalized.isEmpty()) {
            return "";
        }
        String cleaned = normalized;
        while (true) {
            String next = TRAILING_PTIT_CLASS_OR_CODE.matcher(cleaned).replaceFirst("").trim();
            if (next.equals(cleaned) || next.isEmpty()) {
                break;
            }
            cleaned = next;
        }
        return cleaned;
    }
}
