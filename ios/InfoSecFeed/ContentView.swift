import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var store: FeedStore

    var body: some View {
        NavigationStack {
            Group {
                if store.visibleItems.isEmpty && !store.isRefreshing {
                    ContentUnavailableView(
                        "No matching intelligence",
                        systemImage: "shield.lefthalf.filled",
                        description: Text(store.errorMessage ?? "Try another category or search term.")
                    )
                } else {
                    ScrollView {
                        LazyVStack(spacing: 14) {
                            categoryBar
                            ForEach(store.visibleItems) { item in
                                FeedCard(item: item)
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.bottom, 24)
                    }
                    .refreshable { await store.refresh() }
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("InfoSec Feed")
            .searchable(text: $store.searchText, prompt: "Search CVEs, threats and sources")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { Task { await store.refresh() } } label: {
                        if store.isRefreshing { ProgressView() } else { Image(systemName: "arrow.clockwise") }
                    }
                    .disabled(store.isRefreshing)
                    .accessibilityLabel("Refresh security feeds")
                }
            }
        }
        .task { await store.load() }
    }

    private var categoryBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                CategoryChip(title: "All", selected: store.selectedCategory == nil) {
                    store.selectedCategory = nil
                }
                ForEach(FeedCategory.allCases, id: \.self) { category in
                    CategoryChip(title: category.rawValue, selected: store.selectedCategory == category) {
                        store.selectedCategory = category
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }
}

private struct CategoryChip: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(title, action: action)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(selected ? Color.white : Color.primary)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(selected ? Color.accentColor : Color(.secondarySystemGroupedBackground))
            .clipShape(Capsule())
            .buttonStyle(.plain)
    }
}

private struct FeedCard: View {
    let item: FeedItem

    var body: some View {
        Group {
            if let destination = item.url {
                Link(destination: destination) { cardContent }
            } else {
                cardContent
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint(item.url == nil ? "" : "Opens the source article")
    }

    private var cardContent: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let imageURL = item.imageURL {
                AsyncImage(url: imageURL, transaction: .init(animation: .easeOut(duration: 0.2))) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    case .failure:
                        Color.clear
                    default:
                        ZStack { Color(.tertiarySystemFill); ProgressView() }
                    }
                }
                .frame(height: 205)
                .frame(maxWidth: .infinity)
                .clipped()
                .accessibilityHidden(true)
            }

            VStack(alignment: .leading, spacing: 10) {
                if let severity = item.severity {
                    Text(severity)
                        .font(.caption.weight(.heavy))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(severityColor(severity))
                        .clipShape(Capsule())
                }

                Text(item.title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.leading)
                    .lineLimit(4)

                if item.imageURL == nil, !item.summary.isEmpty {
                    Text(item.summary)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                }

                HStack(spacing: 8) {
                    SourceAvatar(name: item.source)
                    Text(item.source).foregroundStyle(.primary).lineLimit(1)
                    Text("·").foregroundStyle(.tertiary)
                    Text(relativeDate(item.published)).foregroundStyle(.secondary).lineLimit(1)
                }
                .font(.caption)
            }
            .padding(16)
        }
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: .black.opacity(0.08), radius: 5, y: 2)
    }

    private func severityColor(_ severity: String) -> Color {
        switch severity {
        case "EXPLOITED": return .purple
        case "CRITICAL": return .red
        default: return .orange
        }
    }

    private func relativeDate(_ date: Date?) -> String {
        guard let date else { return "undated" }
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

private struct SourceAvatar: View {
    let name: String

    var body: some View {
        Text(String(name.prefix(1)).uppercased())
            .font(.caption2.weight(.bold))
            .foregroundStyle(.white)
            .frame(width: 22, height: 22)
            .background(avatarColor)
            .clipShape(Circle())
            .accessibilityHidden(true)
    }

    private var avatarColor: Color {
        let palette: [Color] = [.blue, .indigo, .purple, .teal, .orange, .pink]
        let index = name.unicodeScalars.reduce(0) { ($0 + Int($1.value)) % palette.count }
        return palette[index]
    }
}

#Preview {
    ContentView().environmentObject(FeedStore())
}
