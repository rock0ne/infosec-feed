import SwiftUI

@main
struct InfoSecFeedApp: App {
    @StateObject private var store = FeedStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
        }
    }
}
