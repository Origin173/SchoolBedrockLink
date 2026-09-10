package com.origin173.schoolBedrockLink.security;

public final class HtmlEscaper {

    private HtmlEscaper() {
    }

    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            switch (value.charAt(index)) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(value.charAt(index));
            }
        }
        return escaped.toString();
    }
}
