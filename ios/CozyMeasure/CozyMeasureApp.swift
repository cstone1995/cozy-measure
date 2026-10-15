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

struct FloorPoint: Codable, Hashable { let x: Double; let y: Double }
struct RoomOutline {
    let length: Double; let width: Double; let area: Double; let perimeter: Double; let points: [FloorPoint]
    static func summarize(_ corners: [SIMD3<Float>]) -> RoomOutline? {
        guard corners.count >= 3, corners.count <= 40 else { return nil }
        let p = corners.map { FloorPoint(x: Double($0.x), y: Double($0.z)) }; let n = p.count
        func cross(_ a: FloorPoint,_ b: FloorPoint,_ c: FloorPoint) -> Double { (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x) }
        func on(_ a: FloorPoint,_ b: FloorPoint,_ c: FloorPoint) -> Bool { abs(cross(a,b,c)) < 1e-7 && c.x >= min(a.x,b.x)-1e-7 && c.x <= max(a.x,b.x)+1e-7 && c.y >= min(a.y,b.y)-1e-7 && c.y <= max(a.y,b.y)+1e-7 }
        func intersect(_ a: FloorPoint,_ b: FloorPoint,_ c: FloorPoint,_ d: FloorPoint) -> Bool {
            let x=cross(a,b,c), y=cross(a,b,d), z=cross(c,d,a), w=cross(c,d,b)
            return (x*y < -1e-12 && z*w < -1e-12) || on(a,b,c) || on(a,b,d) || on(c,d,a) || on(c,d,b)
        }
        var area=0.0, perimeter=0.0
        for i in 0..<n {
            let a=p[i], b=p[(i+1)%n]; let edge=hypot(b.x-a.x,b.y-a.y)
            guard edge.isFinite, edge >= 0.1 else { return nil }
            area += a.x*b.y-b.x*a.y; perimeter += edge
            for j in (i+1)..<n {
                if j == i+1 || (i == 0 && j == n-1) { continue }
                if intersect(a,b,p[j],p[(j+1)%n]) { return nil }
            }
        }
        area=abs(area)/2; guard area >= 0.05 else { return nil }
        var bestArea=Double.infinity, length=0.0, width=0.0; var best: [FloorPoint]=[]
        for i in 0..<n {
            let a=p[i], b=p[(i+1)%n], theta=atan2(b.y-a.y,b.x-a.x)
            let rotated=p.map { FloorPoint(x: ($0.x-a.x)*cos(theta)+($0.y-a.y)*sin(theta), y: -($0.x-a.x)*sin(theta)+($0.y-a.y)*cos(theta)) }
            let minX=rotated.map(\.x).min()!, minY=rotated.map(\.y).min()!
            let l=rotated.map(\.x).max()!-minX, w=rotated.map(\.y).max()!-minY
            if l*w < bestArea { bestArea=l*w; length=l; width=w; best=rotated.map { FloorPoint(x:$0.x-minX,y:$0.y-minY) } }
        }
        guard length <= 100, width <= 100 else { return nil }
        return RoomOutline(length:length,width:width,area:area,perimeter:perimeter,points:best)
    }
}

final class RoomMeasure: NSObject, ObservableObject, ARSessionDelegate {
    let view = ARSCNView(frame: .zero)
    @Published var status = "Move slowly to find the floor. Aim at the first floor corner."
    @Published var corners: [SIMD3<Float>] = []
    @Published var closed = false
    var outline: RoomOutline? { RoomOutline.summarize(corners) }
    @Published var permissionDenied = false
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
        guard !closed, corners.count < 40 else { status = "Room complete, or 40-corner limit reached. Review the plan."; return }
        guard let frame = view.session.currentFrame else { status = "Wait for the camera to start."; return }
        guard case .normal = frame.camera.trackingState else { status = "Tracking uncertain. Scan the floor slowly in good light."; return }
        let center=CGPoint(x:view.bounds.midX,y:view.bounds.midY)
        guard let query=view.raycastQuery(from:center,allowing:.existingPlaneGeometry,alignment:.horizontal), let hit=view.session.raycast(query).first else { status = "Floor not found. Move slowly and aim beside the wall."; return }
        let t=hit.worldTransform.columns.3
        let raw=SIMD3<Float>(t.x,t.y,t.z)
        let floor=corners.first?.y ?? raw.y
        guard abs(raw.y-floor) <= 0.08 else { status = "Aim at the same floor level, not furniture or a step."; return }
        let p=SIMD3<Float>(raw.x,floor,raw.z)
        if let last=corners.last, simd_distance(last,p)<0.1 { status = "Too close to the last corner. Aim at the next one."; return }
        let node=SCNNode(geometry:SCNSphere(radius:0.015));node.geometry?.firstMaterial?.diffuse.contents=UIColor.brown
        node.simdPosition=p;view.scene.rootNode.addChildNode(node);anchorNodes.append(node);corners.append(p)
        if corners.count > 1 { addWall(corners[corners.count-2],p) }
        status = "Corner \(corners.count) saved. Follow the wall and add the next corner."
    }
    private var wallNodes: [SCNNode] = []
    private func addWall(_ a: SIMD3<Float>,_ b: SIMD3<Float>) {
        let vertices=[SCNVector3(a.x,a.y,a.z),SCNVector3(b.x,b.y,b.z)]
        let geometry=SCNGeometry(sources:[SCNGeometrySource(vertices:vertices)],elements:[SCNGeometryElement(indices:[Int32(0),Int32(1)],primitiveType:.line)])
        geometry.firstMaterial?.diffuse.contents=UIColor.brown;geometry.firstMaterial?.lightingModel = .constant
        let node=SCNNode(geometry:geometry);view.scene.rootNode.addChildNode(node);wallNodes.append(node)
    }
    func finish() -> Bool {
        guard outline != nil else { status = "Outline overlaps or is too small. Undo a corner and try again."; return false }
        if let a=corners.last, let b=corners.first { addWall(a,b) }
        closed=true;status="Room complete. Review the outline and dimensions.";return true
    }
    func undo() {
        if closed { closed=false } else if !corners.isEmpty { corners.removeLast();anchorNodes.removeLast().removeFromParentNode() }
        wallNodes.forEach{$0.removeFromParentNode()};wallNodes=[]
        if corners.count>1 { for i in 1..<corners.count { addWall(corners[i-1],corners[i]) } }
        status="Aim at the next floor corner."
    }
    func reset() {
        corners=[];closed=false;anchorNodes.forEach{$0.removeFromParentNode()};anchorNodes=[]
        wallNodes.forEach{$0.removeFromParentNode()};wallNodes=[]
        status="Find the floor. Add each corner around the room in order."
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
    var plan: [FloorPoint]? = nil
    var area: Double? = nil
}

struct ContentView: View {
    @StateObject private var model = RoomMeasure()
    @State private var tab = 0
    @State private var roomName = "Living room"
    @State private var plan: [FloorPoint] = []
    @State private var floorArea: Double?
    @State private var savedRooms: [SavedRoom] = (UserDefaults.standard.data(forKey: "cozy_rooms").flatMap { try? JSONDecoder().decode([SavedRoom].self, from: $0) }) ?? []
    @State private var length = ""
    @State private var width = ""
    @State private var roll = "both"
    @State private var allowance = true
    @State private var confirmed = false
    @State private var result: CarpetCut?
    @State private var error = ""
    private func persistRooms() { if let data = try? JSONEncoder().encode(savedRooms) { UserDefaults.standard.set(data, forKey: "cozy_rooms") } }
    private var shopURL: URL {
        var url = URLComponents(string: "https://cozyflooringstore.co.uk/collections/all")!
        let planText = (try? JSONEncoder().encode(plan)).flatMap { String(data:$0,encoding:.utf8) } ?? "[]"
        url.queryItems = [URLQueryItem(name:"cozy_plan",value:planText), URLQueryItem(name: "cozy_room", value: roomName), URLQueryItem(name: "cozy_length", value: String(number(length))), URLQueryItem(name: "cozy_width", value: String(number(width)))]
        return url.url!
    }
    private let brown = Color(red: 0.545, green: 0.459, blue: 0.400)
    private func number(_ s: String) -> Double { Double(s.replacingOccurrences(of: ",", with: ".")) ?? 0 }
    var body: some View {
        TabView(selection: $tab) {
            VStack(spacing: 12) {
                Text("Cozy Measure").font(.largeTitle).bold()
                Text("1 Find the floor. 2 Aim at each corner around the room and tap Add corner. Include door recesses and alcoves. 3 Finish room.").padding(.horizontal)
                ZStack { CameraView(model: model); Image(systemName: "plus").font(.largeTitle).foregroundColor(.white).shadow(radius: 2) }.frame(maxHeight: .infinity)
                Text(model.status).font(.callout).padding(.horizontal).accessibilityAddTraits(.updatesFrequently)
                HStack {
                    Button("Restart") { model.reset(); model.start() }
                    Button("Add corner \(model.corners.count+1)") { model.point() }.buttonStyle(.borderedProminent)
                }
                HStack {
                    Button("Undo") { model.undo() }.disabled(model.corners.isEmpty)
                    Button("Finish room & review") {
                        if model.finish(), let room=model.outline {
                            length=String(format:"%.3f",room.length);width=String(format:"%.3f",room.width)
                            plan=room.points;floorArea=room.area;confirmed=false;result=nil;tab=1
                        }
                    }.disabled(model.corners.count<3 || model.closed)
                }
                Text("Phone estimates need a tape or professional check before ordering.").font(.caption).padding(.bottom)
            }.tabItem { Label("Measure", systemImage: "camera") }.tag(0)
            Form {
                Section("Saved rooms") {
                    TextField("Room name", text: $roomName)
                    ForEach(savedRooms) { room in
                        Button("Load \(room.name): \(room.length)m × \(room.width)m") { length = room.length; width = room.width; roomName = room.name; confirmed = false; result = nil; plan = room.plan ?? []; floorArea = room.area }
                    }.onDelete { offsets in savedRooms.remove(atOffsets: offsets); persistRooms() }
                }
                if !plan.isEmpty {
                    Section("Your floor plan") {
                        FloorPlanView(points:plan).frame(height:240)
                        Text(String(format:"Measured floor area: %.2fm²",floorArea ?? 0))
                        Text("Dimensions below enclose the whole outline. Purchased carpet area can be larger than floor area.")
                    }
                }
                Section("Confirm room measurements") {
                    Text("Metres. Check all corners and recesses. Edit the enclosing dimensions if needed.")
                    TextField("Length in metres", text: Binding(get:{length},set:{length=$0;plan=[];floorArea=nil})).keyboardType(.decimalPad)
                    TextField("Width in metres", text: Binding(get:{width},set:{width=$0;plan=[];floorArea=nil})).keyboardType(.decimalPad)
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
                        Text(String(format: "Room: %.2f m² · Purchased carpet: %.2f m²", floorArea ?? number(length)*number(width), r.area))
                        Text("Orientation may be rotated. Check product minimum length, available widths, pile direction and patterns. Stairs and connected rooms need a separate plan. A single-piece enclosing cut is an estimate, not a seam layout.")
                        ShareLink(item: String(format: "Cozy estimate: room %.3fm × %.3fm; suggested cut %.0fm × %.1fm (%.2fm²). Check before ordering.", number(length),number(width),r.width,r.length,r.area)) { Text("Share estimate") }
                        Button("Save this room") {
                            savedRooms.removeAll { $0.name == roomName }
                            savedRooms.insert(SavedRoom(name: roomName.isEmpty ? "My room" : roomName, length: length, width: width, plan: plan, area: floorArea), at: 0)
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

struct FloorPlanView: View {
    let points: [FloorPoint]
    var body: some View {
        Canvas { context, size in
            guard points.count >= 3 else { return }
            let maxX=max(points.map(\.x).max() ?? 1,0.1), maxY=max(points.map(\.y).max() ?? 1,0.1)
            let scale=min((size.width-80)/maxX,(size.height-80)/maxY)
            let ox=(size.width-maxX*scale)/2, oy=(size.height-maxY*scale)/2
            func point(_ p:FloorPoint)->CGPoint { CGPoint(x:ox+p.x*scale,y:oy+p.y*scale) }
            var path=Path();path.move(to:point(points[0]));for p in points.dropFirst(){path.addLine(to:point(p))};path.closeSubpath()
            context.fill(path,with:.color(.brown.opacity(0.15)));context.stroke(path,with:.color(.brown),lineWidth:3)
            for i in points.indices { let a=points[i], b=points[(i+1)%points.count]
                let midpoint=CGPoint(x:ox+(a.x+b.x)/2*scale,y:oy+(a.y+b.y)/2*scale-12)
                context.draw(Text(String(format:"%.2fm",hypot(b.x-a.x,b.y-a.y))).font(.caption),at:midpoint)
            }
        }.accessibilityLabel("Floor plan showing the measured room outline and wall lengths")
    }
}
