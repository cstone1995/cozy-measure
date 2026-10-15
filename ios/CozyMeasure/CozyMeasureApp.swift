import SwiftUI
import UIKit
import ARKit
import SceneKit
import AVFoundation
import simd

@main
struct CozyMeasureApp: App {
    var body: some Scene { WindowGroup { ContentView() } }
}

struct CarpetCut {
    let width: Double
    let length: Double
    var area: Double { width * length }
    static func best(_ length: Double, _ width: Double, rolls: [Double], allowance: Bool) -> CarpetCut? {
        guard length.isFinite, width.isFinite, length > 0, width > 0 else { return nil }
        let l = length + (allowance ? 0.1 : 0), w = width + (allowance ? 0.1 : 0)
        var cuts: [CarpetCut] = []
        for roll in rolls {
            if w <= roll + 1e-9 { cuts.append(.init(width: roll, length: ceil((l - 1e-9) * 10) / 10)) }
            if l <= roll + 1e-9 { cuts.append(.init(width: roll, length: ceil((w - 1e-9) * 10) / 10)) }
        }
        return cuts.sorted { abs($0.area - $1.area) < 1e-9 ? $0.width < $1.width : $0.area < $1.area }.first
    }
}

final class RoomMeasure: NSObject, ObservableObject, ARSessionDelegate {
    let view = ARSCNView(frame: .zero)
    @Published var status = "Move slowly to find the floor. Aim at the first floor corner."
    @Published var distances: [Double] = []
    @Published var permissionDenied = false
    private var first: SIMD3<Float>?
    private var anchorNodes: [SCNNode] = []
    private var interrupted = false
    func start() {
        guard ARWorldTrackingConfiguration.isSupported else {
            status = "Camera measuring is unavailable on this device. Enter tape measurements instead."; return
        }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: run()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async { granted ? self.run() : self.deny() }
            }
        default: deny()
        }
    }
    private func deny() { permissionDenied = true; status = "Allow camera access in Settings, or enter tape measurements." }
    private func run() {
        permissionDenied = false
        let config = ARWorldTrackingConfiguration()
        config.planeDetection = [.horizontal]
        view.session.delegate = self
        view.session.run(config, options: interrupted ? [.resetTracking, .removeExistingAnchors] : [])
        interrupted = false
    }
    func point() {
        guard distances.count < 2 else { status = "Both dimensions captured. Confirm them in Carpet size."; return }
        guard let frame = view.session.currentFrame else { status = "Wait for the camera to start."; return }
        guard case .normal = frame.camera.trackingState else { status = "Tracking is uncertain. Move slowly in good light and try again."; return }
        let center = CGPoint(x: view.bounds.midX, y: view.bounds.midY)
        guard let query = view.raycastQuery(from: center, allowing: .existingPlaneGeometry, alignment: .horizontal),
              let hit = view.session.raycast(query).first else { status = "No floor found here yet. Scan the floor slowly."; return }
        let t = hit.worldTransform.columns.3, p = SIMD3<Float>(t.x,t.y,t.z)
        if let a = first, abs(a.y - p.y) > 0.08 { status = "Aim at the same floor level, not furniture or another step."; return }
        let node = SCNNode(geometry: SCNSphere(radius: 0.015))
        node.geometry?.firstMaterial?.diffuse.contents = UIColor.systemOrange
        node.simdPosition = p; view.scene.rootNode.addChildNode(node); anchorNodes.append(node)
        if let a = first {
            let distance = Double(simd_distance(SIMD2<Float>(a.x,a.z), SIMD2<Float>(p.x,p.z)))
            guard distance >= 0.1 else { node.removeFromParentNode(); anchorNodes.removeLast(); status = "Points are too close. Try the opposite floor corner."; return }
            distances.append(distance); first = nil
            status = distances.count == 1 ? "Length saved. Now capture two points for the widest width." : "Width saved. Open Carpet size and confirm both dimensions."
        } else { first = p; status = "First point saved. Aim at the opposite end of this dimension." }
    }
    func reset() {
        first = nil; distances = []; anchorNodes.forEach { $0.removeFromParentNode() }; anchorNodes = []
        status = "Aim at the first corner of the longest room length."
    }
    func sessionWasInterrupted(_ session: ARSession) { interrupted = true; DispatchQueue.main.async { self.reset(); self.status = "Camera interrupted. Restart and measure again." } }
    func session(_ session: ARSession, didFailWithError error: Error) { DispatchQueue.main.async { self.reset(); self.status = "Camera tracking stopped. Restart and measure again." } }
}

struct CameraView: UIViewRepresentable {
    let model: RoomMeasure
    func makeUIView(context: Context) -> ARSCNView { model.view }
    func updateUIView(_ uiView: ARSCNView, context: Context) {}
}

struct SavedRoom: Codable, Identifiable {
    var id: UUID = UUID()
    let name: String
    let length: String
    let width: String
}

struct ContentView: View {
    @StateObject private var model = RoomMeasure()
    @State private var tab = 0
    @State private var roomName = "Living room"
    @State private var savedRooms: [SavedRoom] = (UserDefaults.standard.data(forKey: "cozy_rooms").flatMap { try? JSONDecoder().decode([SavedRoom].self, from: $0) }) ?? []
    @State private var length = "", width = ""
    @State private var roll = "both"
    @State private var allowance = true, confirmed = false
    @State private var result: CarpetCut?
    @State private var error = ""
    private func persistRooms() { if let data = try? JSONEncoder().encode(savedRooms) { UserDefaults.standard.set(data, forKey: "cozy_rooms") } }
    private var shopURL: URL {
        var url = URLComponents(string: "https://cozyflooringstore.co.uk/collections/all")!
        url.queryItems = [URLQueryItem(name: "cozy_room", value: roomName), URLQueryItem(name: "cozy_length", value: String(number(length))), URLQueryItem(name: "cozy_width", value: String(number(width)))]
        return url.url!
    }
    private let brown = Color(red: 0.545, green: 0.459, blue: 0.400)
    private func number(_ s: String) -> Double { Double(s.replacingOccurrences(of: ",", with: ".")) ?? 0 }
    var body: some View {
        TabView(selection: $tab) {
            VStack(spacing: 12) {
                Text("Cozy Measure").font(.largeTitle).bold()
                Text("Measure the longest length, then the widest width. Include door recesses. Rectangular rooms only.").padding(.horizontal)
                ZStack { CameraView(model: model); Image(systemName: "plus").font(.largeTitle).foregroundColor(.white).shadow(radius: 2) }.frame(maxHeight: .infinity)
                Text(model.status).font(.callout).padding(.horizontal).accessibilityAddTraits(.updatesFrequently)
                HStack {
                    Button("Restart") { model.reset(); model.start() }
                    Button("Mark floor point") { model.point() }.buttonStyle(.borderedProminent)
                }
                Button("Confirm dimensions") {
                    if model.distances.count == 2 {
                        length = String(format: "%.3f", model.distances[0]); width = String(format: "%.3f", model.distances[1]); confirmed = false; result = nil
                    }
                    tab = 1
                }
                Text("Phone estimates need a tape or professional check before ordering.").font(.caption).padding(.bottom)
            }.tabItem { Label("Measure", systemImage: "camera") }.tag(0)
            Form {
                Section("Saved rooms") {
                    TextField("Room name", text: $roomName)
                    ForEach(savedRooms) { room in
                        Button("Load \(room.name): \(room.length)m × \(room.width)m") { length = room.length; width = room.width; roomName = room.name; confirmed = false; result = nil }
                    }.onDelete { offsets in savedRooms.remove(atOffsets: offsets); persistRooms() }
                }
                Section("Confirm room measurements") {
                    Text("Enter metres. Check the longest and widest points, including recesses.")
                    TextField("Length in metres", text: $length).keyboardType(.decimalPad)
                    TextField("Width in metres", text: $width).keyboardType(.decimalPad)
                    Picker("Carpet widths", selection: $roll) { Text("4m and 5m").tag("both"); Text("4m only").tag("4"); Text("5m only").tag("5") }
                    Toggle("Add 10cm to each dimension", isOn: $allowance)
                    Text("Untick if allowance is already included. Cut length rounds up to 10cm.").font(.caption)
                    Toggle("I have checked these dimensions", isOn: $confirmed)
                    Button("Calculate carpet size") {
                        result = nil
                        let l = number(length), w = number(width)
                        guard confirmed, l > 0, w > 0, l.isFinite, w.isFinite else { error = "Enter positive dimensions and confirm them."; return }
                        result = CarpetCut.best(l, w, rolls: roll == "both" ? [4,5] : [Double(roll)!], allowance: allowance)
                        error = result == nil ? "A join or cutting plan is needed. Ask Cozy before ordering." : ""
                    }
                    if !error.isEmpty { Text(error).foregroundColor(.red) }
                }
                if let r = result {
                    Section("Suggested single-piece cut") {
                        Text(String(format: "%.0fm × %.1fm", r.width, r.length)).font(.title)
                        Text(String(format: "Room: %.2f m² · Purchased carpet: %.2f m²", number(length)*number(width), r.area))
                        Text("Orientation may be rotated. Check product minimum length, available widths, pile direction and patterns. Stairs and connected rooms need a separate plan.")
                        ShareLink(item: String(format: "Cozy estimate: room %.3fm × %.3fm; suggested cut %.0fm × %.1fm (%.2fm²). Check before ordering.", number(length),number(width),r.width,r.length,r.area)) { Text("Share estimate") }
                        Button("Save this room") {
                            savedRooms.removeAll { $0.name == roomName }
                            savedRooms.insert(SavedRoom(name: roomName.isEmpty ? "My room" : roomName, length: length, width: width), at: 0)
                            savedRooms = Array(savedRooms.prefix(20)); persistRooms()
                        }
                        Link("Shop with these measurements", destination: shopURL)
                    }
                }
            }.tabItem { Label("Carpet size", systemImage: "ruler") }.tag(1)
        }.tint(brown)
        .onAppear { model.start() }
        .onChange(of: length) { _ in result = nil; confirmed = false }
        .onChange(of: width) { _ in result = nil; confirmed = false }
        .onChange(of: roll) { _ in result = nil }
        .onChange(of: allowance) { _ in result = nil }
        .onChange(of: confirmed) { value in if !value { result = nil } }
        .onChange(of: tab) { value in if value == 0 { model.start() } else { model.view.session.pause() } }
    }
}
