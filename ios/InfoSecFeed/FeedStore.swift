import Foundation
import Combine

@MainActor
final class FeedStore: ObservableObject {
    @Published private(set) var items: [FeedItem] = []
    @Published private(set) var isRefreshing = false
    @Published private(set) var errorMessage: String?
    @Published var selectedCategory: FeedCategory?
    @Published var searchText = ""

    private let repository = FeedRepository()
    private var didLoad = false

    var visibleItems: [FeedItem] {
        items.filter { item in
            let categoryMatches = selectedCategory == nil || item.category == selectedCategory
            let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
            let searchMatches = query.isEmpty || item.title.localizedCaseInsensitiveContains(query)
                || item.summary.localizedCaseInsensitiveContains(query)
                || item.source.localizedCaseInsensitiveContains(query)
            return categoryMatches && searchMatches
        }
    }

    func load() async {
        guard !didLoad else { return }
        didLoad = true
        let cached = repository.cached()
        if !cached.isEmpty { items = cached }
        if cached.isEmpty { await refresh() }
    }

    func refresh() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        errorMessage = nil
        let refreshed = await repository.refresh()
        if refreshed.isEmpty {
            if items.isEmpty { errorMessage = "No sources responded. Check your connection and try again." }
        } else {
            items = refreshed
        }
        isRefreshing = false
    }
}
