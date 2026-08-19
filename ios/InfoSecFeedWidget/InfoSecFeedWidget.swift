import SwiftUI
import WidgetKit

private struct WidgetItem: Identifiable, Hashable {
    let id: String
    let title: String
    let severity: String
    let url: URL
}

private struct SecurityEntry: TimelineEntry {
    let date: Date
    let items: [WidgetItem]
}

private struct SecurityProvider: TimelineProvider {
    func placeholder(in context: Context) -> SecurityEntry {
        SecurityEntry(date: Date(), items: Self.samples)
    }

    func getSnapshot(in context: Context, completion: @escaping (SecurityEntry) -> Void) {
        completion(SecurityEntry(date: Date(), items: Self.samples))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SecurityEntry>) -> Void) {
        Task {
            let items = await fetchKnownExploited()
            let entry = SecurityEntry(date: Date(), items: items.isEmpty ? Self.samples : items)
            completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(30 * 60))))
        }
    }

    private func fetchKnownExploited() async -> [WidgetItem] {
        guard let url = URL(string: "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json") else {
            return []
        }
        var request = URLRequest(url: url, timeoutInterval: 15)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("InfoSecFeed-iOS-Widget/1.0", forHTTPHeaderField: "User-Agent")
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode),
                  data.count <= 12 * 1_024 * 1_024,
                  let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let values = root["vulnerabilities"] as? [[String: Any]] else { return [] }
            return values.suffix(6).reversed().compactMap { value in
                guard let cve = value["cveID"] as? String,
                      let link = URL(string: "https://nvd.nist.gov/vuln/detail/\(cve)") else { return nil }
                return WidgetItem(
                    id: cve,
                    title: "\(cve) — \(value["vulnerabilityName"] as? String ?? "Known exploited vulnerability")",
                    severity: "EXPLOITED",
                    url: link
                )
            }
        } catch {
            return []
        }
    }

    private static let samples = [
        WidgetItem(
            id: "sample",
            title: "Known exploited vulnerabilities and critical security updates",
            severity: "INFOSEC",
            url: URL(string: "https://www.cisa.gov/known-exploited-vulnerabilities-catalog")!
        ),
    ]
}

private struct SecurityWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: SecurityEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "shield.lefthalf.filled").foregroundStyle(.blue)
                Text("InfoSec Feed").font(.headline)
                Spacer()
                Text("CISA KEV").font(.caption2).foregroundStyle(.secondary)
            }
            ForEach(entry.items.prefix(family == .systemLarge ? 6 : 3)) { item in
                Link(destination: item.url) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.severity)
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundStyle(.red)
                        Text(item.title)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                if item.id != entry.items.prefix(family == .systemLarge ? 6 : 3).last?.id {
                    Divider()
                }
            }
            Spacer(minLength: 0)
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }
}

@main
struct InfoSecFeedWidget: Widget {
    let kind = "InfoSecFeedWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: SecurityProvider()) { entry in
            SecurityWidgetView(entry: entry)
        }
        .configurationDisplayName("InfoSec Feed")
        .description("Known exploited vulnerabilities and high-signal security updates.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}
