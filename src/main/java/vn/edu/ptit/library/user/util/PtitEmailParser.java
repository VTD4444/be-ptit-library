package vn.edu.ptit.library.user.util;

import vn.edu.ptit.library.common.enums.UserType;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PtitEmailParser {

    // Mau ma sinh vien PTIT pho bien: B21DCCN001, B22DCVT058, N20DCAT012, C21...
    private static final Pattern STUDENT_CODE_PATTERN = Pattern.compile(
            "(?:^|[._-])([BCN](\\d{2})[A-Z]{2,6}\\d{2,4})(?:$|[._-])",
            Pattern.CASE_INSENSITIVE
    );

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
            String studentCode = matcher.group(1).toUpperCase(Locale.ROOT);
            int yearTwoDigits = Integer.parseInt(matcher.group(2));
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
}
