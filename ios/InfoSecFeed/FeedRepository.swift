import Foundation

enum FeedError: LocalizedError {
    case invalidResponse
    case http(Int)
    case responseTooLarge

    var errorDescription: String? {
        switch self {
        case .invalidResponse: return "The source returned an invalid response."
        case .http(let status): return "The source returned HTTP \(status)."
        case .responseTooLarge: return "The source response exceeded the safety limit."
        }
    }
}

struct NetworkClient: Sendable {
    private static let maximumBytes = 12 * 1_024 * 1_024
    private let session: URLSession

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 30
        configuration.httpMaximumConnectionsPerHost = 2
        configuration.urlCache = nil
        session = URLSession(configuration: configuration)
    }

    func get(_ url: URL, accept: String, reddit: Bool = false) async throws -> Data {
        var request = URLRequest(url: url)
        request.setValue(accept, forHTTPHeaderField: "Accept")
        request.setValue("en-GB,en;q=0.9", forHTTPHeaderField: "Accept-Language")
        request.setValue(
            reddit
                ? "ios:uk.cybertecpro.infosecfeed:1.0 (open-source security feed reader)"
                : "InfoSecFeed-iOS/1.0 (+https://github.com/rock0ne/infosec-feed)",
            forHTTPHeaderField: "User-Agent"
        )
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw FeedError.invalidResponse }
        guard (200...299).contains(http.statusCode) else { throw FeedError.http(http.statusCode) }
        guard data.count <= Self.maximumBytes else { throw FeedError.responseTooLarge }
        return data
    }
}

struct FeedRepository: Sendable {
    private let client = NetworkClient()

    private var cacheURL: URL {
        let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return directory.appendingPathComponent("infosec-feed.json")
    }

    func cached() -> [FeedItem] {
        guard let data = try? Data(contentsOf: cacheURL) else { return [] }
        return (try? JSONDecoder().decode([FeedItem].self, from: data)) ?? []
    }

    func refresh() async -> [FeedItem] {
        var collected: [FeedItem] = []

        await withTaskGroup(of: [FeedItem].self) { group in
            group.addTask { (try? await fetchKEV()) ?? [] }
            group.addTask { (try? await fetchNVD(severity: "CRITICAL")) ?? [] }
            group.addTask { (try? await fetchNVD(severity: "HIGH")) ?? [] }
            group.addTask { (try? await fetchGitHub()) ?? [] }
            for await items in group { collected.append(contentsOf: items) }
        }

        let regular = Sources.rss.filter { !$0.url.host.orEmpty.contains("reddit.com") }
        for start in stride(from: 0, to: regular.count, by: 8) {
            let batch = Array(regular[start..<min(start + 8, regular.count)])
            await withTaskGroup(of: [FeedItem].self) { group in
                for source in batch {
                    group.addTask { (try? await fetchRSS(source)) ?? [] }
                }
                for await items in group { collected.append(contentsOf: items) }
            }
        }

        for source in Sources.rss.filter({ $0.url.host.orEmpty.contains("reddit.com") }) {
            collected.append(contentsOf: (try? await fetchRSS(source)) ?? [])
            try? await Task.sleep(for: .seconds(2.5))
        }

        var unique: [String: FeedItem] = [:]
        for item in collected where unique[item.id] == nil { unique[item.id] = item }
        let merged = Array(unique.values).sorted { $0.rank > $1.rank }.prefix(500)
        let result = Array(merged)
        if !result.isEmpty, let data = try? JSONEncoder().encode(result) {
            try? data.write(to: cacheURL, options: .atomic)
        }
        return result
    }

    private func fetchRSS(_ source: FeedSource) async throws -> [FeedItem] {
        let data = try await client.get(
            source.url,
            accept: "application/rss+xml, application/atom+xml, application/xml, text/xml",
            reddit: source.url.host.orEmpty.contains("reddit.com")
        )
        let prefix = String(decoding: data.prefix(200), as: UTF8.self)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        guard !prefix.hasPrefix("<!doctype html"), !prefix.hasPrefix("<html") else {
            throw FeedError.invalidResponse
        }
        return try RSSFeedParser.parse(data: data, source: source)
    }

    private func fetchKEV() async throws -> [FeedItem] {
        let data = try await client.get(Sources.cisaKEV, accept: "application/json")
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let vulnerabilities = root["vulnerabilities"] as? [[String: Any]] else { return [] }
        return vulnerabilities.suffix(40).compactMap { value in
            guard let cve = value["cveID"] as? String, !cve.isEmpty else { return nil }
            let name = value["vulnerabilityName"] as? String ?? "Known exploited vulnerability"
            let vendor = value["vendorProject"] as? String ?? ""
            let product = value["product"] as? String ?? ""
            let detail = value["shortDescription"] as? String ?? ""
            let due = value["dueDate"] as? String ?? ""
            let summary = "\(vendor) \(product)" + (due.isEmpty ? "" : " · remediate by \(due)")
                + (detail.isEmpty ? "" : "\n\(detail)")
            return FeedItem(
                id: "kev:\(cve)", title: "\(cve) — \(name)", summary: summary,
                source: "CISA KEV", url: URL(string: "https://nvd.nist.gov/vuln/detail/\(cve)"),
                published: DateParser.day(value["dateAdded"] as? String), severity: "EXPLOITED",
                category: .alerts, imageURL: nil
            )
        }
    }

    private func fetchNVD(severity: String) async throws -> [FeedItem] {
        var components = URLComponents(url: Sources.nvd, resolvingAgainstBaseURL: false)!
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let now = Date()
        components.queryItems = [
            .init(name: "pubStartDate", value: formatter.string(from: now.addingTimeInterval(-7 * 86_400))),
            .init(name: "pubEndDate", value: formatter.string(from: now)),
            .init(name: "cvssV3Severity", value: severity),
            .init(name: "resultsPerPage", value: "25"),
        ]
        let data = try await client.get(components.url!, accept: "application/json")
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let vulnerabilities = root["vulnerabilities"] as? [[String: Any]] else { return [] }
        return vulnerabilities.compactMap { wrapper in
            guard let cve = wrapper["cve"] as? [String: Any],
                  let identifier = cve["id"] as? String else { return nil }
            let descriptions = cve["descriptions"] as? [[String: Any]] ?? []
            let summary = descriptions.first { $0["lang"] as? String == "en" }?["value"] as? String ?? ""
            return FeedItem(
                id: "nvd:\(identifier)", title: identifier, summary: String(summary.prefix(400)),
                source: "NVD", url: URL(string: "https://nvd.nist.gov/vuln/detail/\(identifier)"),
                published: DateParser.iso(cve["published"] as? String), severity: severity,
                category: .vulnerabilities, imageURL: nil
            )
        }
    }

    private func fetchGitHub() async throws -> [FeedItem] {
        var components = URLComponents(string: Sources.githubSearch)!
        let since = DateParser.dayString(Date().addingTimeInterval(-7 * 86_400))
        components.queryItems = [
            .init(name: "q", value: "topic:security pushed:>\(since)"),
            .init(name: "sort", value: "stars"), .init(name: "order", value: "desc"),
            .init(name: "per_page", value: "10"),
        ]
        let data = try await client.get(components.url!, accept: "application/vnd.github+json")
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let repositories = root["items"] as? [[String: Any]] else { return [] }
        return repositories.compactMap { repository in
            guard let identifier = repository["id"] as? Int,
                  let name = repository["full_name"] as? String else { return nil }
            let stars = repository["stargazers_count"] as? Int ?? 0
            let detail = repository["description"] as? String ?? ""
            return FeedItem(
                id: "gh:\(identifier)", title: name, summary: "★ \(stars) · \(detail)",
                source: "GitHub", url: URL(string: repository["html_url"] as? String ?? ""),
                published: DateParser.iso(repository["pushed_at"] as? String), severity: nil,
                category: .community, imageURL: nil
            )
        }
    }
}

private extension Optional where Wrapped == String {
    var orEmpty: String { self ?? "" }
}

enum DateParser {
    private static func dayFormatter() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }

    static func dayString(_ value: Date) -> String { dayFormatter().string(from: value) }

    static func iso(_ value: String?) -> Date? {
        guard let value else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: value) ?? ISO8601DateFormatter().date(from: value)
    }

    static func day(_ value: String?) -> Date? {
        guard let value else { return nil }
        return dayFormatter().date(from: value)
    }

    static func rss(_ value: String) -> Date? {
        let formats = [
            "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yy HH:mm:ss Z", "EEE, dd MMM yy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm Z", "MMM dd, yyyy HH:mm:ssZ",
            "MMM dd, yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd",
        ]
        for format in formats {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.isLenient = false
            formatter.dateFormat = format
            if let date = formatter.date(from: value.trimmingCharacters(in: .whitespacesAndNewlines)) {
                return date
            }
        }
        return nil
    }
}
