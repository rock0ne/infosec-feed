import Foundation
import FoundationXML

final class RSSFeedParser: NSObject, XMLParserDelegate {
    private let source: FeedSource
    private var items: [FeedItem] = []
    private var insideEntry = false
    private var currentElement = ""
    private var text = ""
    private var title = ""
    private var link = ""
    private var summary = ""
    private var date = ""
    private var image = ""

    private init(source: FeedSource) { self.source = source }

    static func parse(data: Data, source: FeedSource) throws -> [FeedItem] {
        let delegate = RSSFeedParser(source: source)
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        parser.shouldProcessNamespaces = false
        guard parser.parse() else { throw parser.parserError ?? FeedError.invalidResponse }
        return delegate.items
    }

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        let name = (qName ?? elementName).lowercased()
        currentElement = name
        text = ""
        if name == "item" || name == "entry" {
            insideEntry = true
            title = ""; link = ""; summary = ""; date = ""; image = ""
        }
        guard insideEntry else { return }
        if name == "link", let href = attributeDict["href"], !href.isEmpty { link = href }
        if name == "enclosure", attributeDict["type"]?.lowercased().hasPrefix("image/") == true {
            image = image.isEmpty ? attributeDict["url"] ?? "" : image
        }
        if name == "media:content" || name == "media:thumbnail" {
            image = image.isEmpty ? attributeDict["url"] ?? "" : image
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if insideEntry { text += string }
    }

    func parser(_ parser: XMLParser, foundCDATA CDATABlock: Data) {
        if insideEntry { text += String(decoding: CDATABlock, as: UTF8.self) }
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        let name = (qName ?? elementName).lowercased()
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard insideEntry else { return }
        switch name {
        case "title": if title.isEmpty { title = value }
        case "link": if link.isEmpty { link = value }
        case "description", "summary", "content", "content:encoded":
            if summary.isEmpty { summary = value }
            if image.isEmpty { image = Self.firstImage(in: value) ?? "" }
        case "pubdate", "published", "updated", "dc:date", "date":
            if date.isEmpty { date = value }
        case "item", "entry":
            insideEntry = false
            if !title.isEmpty {
                let target = URL(string: link, relativeTo: source.url)?.absoluteURL
                let imageURL = Self.safeImageURL(image, relativeTo: target ?? source.url)
                items.append(FeedItem(
                    id: "rss:\(source.name):\(link.isEmpty ? title : link)",
                    title: Self.decodeEntities(title), summary: Self.plainText(summary, limit: 400),
                    source: source.name, url: target, published: DateParser.rss(date), severity: nil,
                    category: source.category, imageURL: imageURL
                ))
            }
        default: break
        }
        currentElement = ""
        text = ""
    }

    private static func firstImage(in html: String) -> String? {
        let pattern = #"<img\b[^>]*?\bsrc\s*=\s*(?:[\"']([^\"']+)[\"']|([^\s>]+))"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
              let match = regex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)) else {
            return nil
        }
        for index in 1...2 where match.range(at: index).location != NSNotFound {
            if let range = Range(match.range(at: index), in: html) { return String(html[range]) }
        }
        return nil
    }

    private static func safeImageURL(_ candidate: String, relativeTo base: URL) -> URL? {
        guard !candidate.isEmpty, candidate.count <= 4_096 else { return nil }
        let decoded = candidate.replacingOccurrences(of: "&amp;", with: "&")
        let value = decoded.hasPrefix("//") ? "https:\(decoded)" : decoded
        guard let resolved = URL(string: value, relativeTo: base)?.absoluteURL,
              var components = URLComponents(url: resolved, resolvingAgainstBaseURL: false) else { return nil }
        if components.scheme == "http" { components.scheme = "https" }
        guard components.scheme == "https", components.user == nil, components.password == nil,
              let host = components.host?.lowercased(), !isLocalHost(host) else {
            return nil
        }
        return components.url
    }

    private static func isLocalHost(_ host: String) -> Bool {
        if host == "localhost" || host.hasSuffix(".local") || host.hasSuffix(".internal") { return true }
        if host == "::1" || (host.contains(":") &&
            (host.hasPrefix("fc") || host.hasPrefix("fd") || host.hasPrefix("fe80:"))) {
            return true
        }
        let octets = host.split(separator: ".").compactMap { Int($0) }
        guard octets.count == 4 else { return false }
        if octets[0] == 10 || octets[0] == 127 || octets[0] == 0 { return true }
        if octets[0] == 169 && octets[1] == 254 { return true }
        if octets[0] == 192 && octets[1] == 168 { return true }
        return octets[0] == 172 && (16...31).contains(octets[1])
    }

    private static func plainText(_ html: String, limit: Int) -> String {
        let withoutTags = html.replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
        let collapsed = decodeEntities(withoutTags)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return String(collapsed.prefix(limit))
    }

    private static func decodeEntities(_ value: String) -> String {
        value.replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&nbsp;", with: " ")
    }
}
