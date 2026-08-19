import XCTest
@testable import InfoSecFeed

final class RSSFeedParserTests: XCTestCase {
    func testParsesRSSImageAndNormalisesCleartext() throws {
        let xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><item>
          <title>CVE intelligence</title>
          <link>https://news.example/posts/1</link>
          <description><![CDATA[<p>Details</p><img src="http://cdn.example/story.jpg">]]></description>
          <pubDate>Wed, 19 Aug 2026 12:00:00 +0000</pubDate>
        </item></channel></rss>
        """
        let source = FeedSource(
            name: "Test Source", url: URL(string: "https://news.example/feed")!, category: .news
        )

        let items = try RSSFeedParser.parse(data: Data(xml.utf8), source: source)

        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items[0].title, "CVE intelligence")
        XCTAssertEqual(items[0].summary, "Details")
        XCTAssertEqual(items[0].imageURL?.absoluteString, "https://cdn.example/story.jpg")
        XCTAssertNotNil(items[0].published)
    }

    func testUnparseableDateRemainsUndated() throws {
        let xml = "<rss><channel><item><title>Old item</title><pubDate>not-a-date</pubDate></item></channel></rss>"
        let source = FeedSource(
            name: "Test Source", url: URL(string: "https://news.example/feed")!, category: .news
        )

        let item = try XCTUnwrap(RSSFeedParser.parse(data: Data(xml.utf8), source: source).first)

        XCTAssertNil(item.published)
        XCTAssertEqual(item.rank, -.greatestFiniteMagnitude)
    }
}
