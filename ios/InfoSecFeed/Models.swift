import Foundation

enum FeedCategory: String, CaseIterable, Codable, Sendable {
    case alerts = "Alerts"
    case vulnerabilities = "Vulns"
    case intelligence = "Threat Intel"
    case news = "News"
    case research = "Research"
    case community = "Community"
}

struct FeedSource: Hashable, Sendable {
    let name: String
    let url: URL
    let category: FeedCategory
}

struct FeedItem: Identifiable, Codable, Hashable, Sendable {
    let id: String
    let title: String
    let summary: String
    let source: String
    let url: URL?
    let published: Date?
    let severity: String?
    let category: FeedCategory
    let imageURL: URL?
}

extension FeedItem {
    var rank: TimeInterval {
        guard let published else { return -.greatestFiniteMagnitude }
        let boost: TimeInterval
        switch severity {
        case "EXPLOITED": boost = 72 * 3_600
        case "CRITICAL": boost = 18 * 3_600
        case "HIGH": boost = 5 * 3_600
        default: boost = source == "GitHub" ? -30 * 3_600 : 0
        }
        return published.timeIntervalSince1970 + boost
    }
}
