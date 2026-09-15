import Foundation

/// Body-text normalisation for CPaaS template/OTP messages.
///
/// The portal composes a template body as plain text with real line breaks and literal
/// "•" bullets, and the "Sample Preview" renders it verbatim. On the wire that body reaches
/// us in one of three shapes depending on which portal/API path produced it: real newlines,
/// `<br>` / `<br />` tags, or newlines that were escaped twice (`\n` as two characters)
/// while being embedded in the cpaas_json blob.
///
/// The call sites used to strip only the two exact spellings `<br />` and `<br>`, so a
/// `<br/>` or a double-escaped newline survived into the label and the paragraphs ran
/// together. Mirrors `TemplateText` on Android.
enum TemplateText {

    /// `<br>`, `<br/>`, `<br />`, any casing.
    private static let brTag = try! NSRegularExpression(pattern: "<br\\s*/?>", options: [.caseInsensitive])

    /// `\n`, `\r\n` or `\r` that arrived escaped as literal backslash sequences.
    private static let escapedNewline = try! NSRegularExpression(pattern: "\\\\r\\\\n|\\\\n|\\\\r")

    /// Collapses every line-break spelling the backend uses into real newlines.
    static func plain(_ raw: String?) -> String {
        guard let src = raw, !src.isEmpty else { return "" }
        let range = { (s: String) in NSRange(s.startIndex..., in: s) }
        var out = escapedNewline.stringByReplacingMatches(
            in: src, range: range(src), withTemplate: "\n")
        out = brTag.stringByReplacingMatches(
            in: out, range: range(out), withTemplate: "\n")
        out = out.replacingOccurrences(of: "\r\n", with: "\n")
                 .replacingOccurrences(of: "\r", with: "\n")
        while out.hasSuffix("\n") || out.hasSuffix(" ") { out.removeLast() }
        return out
    }
}
