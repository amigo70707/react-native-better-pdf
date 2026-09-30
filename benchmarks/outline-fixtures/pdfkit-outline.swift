// Reference reader and PDFKit fixture writer; see README.md in this folder.
import PDFKit
import Foundation

func dump(_ path: String) {
  guard let doc = PDFDocument(url: URL(fileURLWithPath: path)) else { print("\(path): cannot open"); return }
  var lines: [String] = []
  func walk(_ node: PDFOutline, _ depth: Int) {
    for i in 0..<node.numberOfChildren {
      guard let child = node.child(at: i) else { continue }
      var page = "-"
      if let dest = child.destination, let p = dest.page { page = String(doc.index(for: p)) }
      else if let action = child.action as? PDFActionGoTo, let p = action.destination.page { page = String(doc.index(for: p)) }
      lines.append("\(depth)\t\(page)\t\(child.label ?? "")")
      if depth < 200 { walk(child, depth + 1) }
    }
  }
  if let root = doc.outlineRoot { walk(root, 0) }
  print("== \((path as NSString).lastPathComponent): \(doc.pageCount) pages, \(lines.count) entries")
  lines.prefix(12).forEach { print("   " + $0) }
}

let args = CommandLine.arguments.dropFirst()
if args.first == "--write-pdfkit" {
  // Re-serialize with PDFKit and build a fresh outline through its API.
  let src = PDFDocument(url: URL(fileURLWithPath: Array(args)[1]))!
  let root = PDFOutline()
  let titles: [(String, Int, Int)] = [("Front matter", 0, 0), ("Część pierwsza", 1, 0), ("Rozdział 1.1", 2, 1), ("Rozdział 1.2", 3, 1), ("Anhang — Übersicht", 9, 0)]
  var parents: [PDFOutline] = [root]
  for (title, page, depth) in titles {
    let item = PDFOutline()
    item.label = title
    item.destination = PDFDestination(page: src.page(at: page)!, at: CGPoint(x: 0, y: 842))
    let parent = parents[depth]
    parent.insertChild(item, at: parent.numberOfChildren)
    if parents.count > depth + 1 { parents[depth + 1] = item } else { parents.append(item) }
  }
  src.outlineRoot = root
  src.write(to: URL(fileURLWithPath: Array(args)[2]))
} else {
  args.forEach(dump)
}
