package com.mcmiddleearth.architect.slabSmooth.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a block state looks like in a resource pack: the octants its models fill, and the texture on each side.
 * Port of the prototype's model probe (SlabSmooth/slabsmooth/probe.py); models and blockstates not in the pack
 * come from the vanilla client jar, like on the client.
 */
public final class Models {

    /** The widest and highest texture read, in pixels; a bigger one counts as missing. */
    public static final int MAX_TEXTURE_SIDE = 4096;

    /** A model as a block state draws it, turned by x, then y (degrees). */
    public record Placed(String model, int x, int y) {
    }

    /** The texture a side of a block shows, its average colour (0xRRGGBB, tinted like plains grass where the face
     *  is tinted; -1 if the image can't be read) and its contrast (standard deviation of brightness, 0-255). */
    public record Face(String texture, int rgb, double contrast) {
    }

    private record Stats(int rgb, double contrast) {
    }

    private static final Stats NO_IMAGE = new Stats(-1, 0);

    private static final String[] FACES = {"east", "west", "south", "north", "up", "down"}; // Octants direction order
    private static final int[][] NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
    private static final int[] GRASS_TINT = {145, 189, 89};

    private final Resources resources;
    private final Map<String, Optional<JsonObject>> json = new ConcurrentHashMap<>();
    private final Map<String, Stats> colors = new ConcurrentHashMap<>();
    private final Map<String, Optional<Integer>> cleanMasks = new ConcurrentHashMap<>();
    private final Map<String, Optional<Face>> faces = new ConcurrentHashMap<>();

    public Models(Resources resources) {
        this.resources = resources;
    }

    private static String[] split(String location) {
        int colon = location.indexOf(':');
        return colon < 0 ? new String[] {"minecraft", location} : new String[] {location.substring(0, colon), location.substring(colon + 1)};
    }

    private JsonObject readJson(String kind, String location) {
        String[] nsPath = split(location);
        String path = "assets/" + nsPath[0] + "/" + kind + "/" + nsPath[1] + ".json";
        return json.computeIfAbsent(path, p -> {
            byte[] raw = resources.read(p);
            if (raw == null) {
                return Optional.empty();
            }
            try {
                JsonElement e = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
                return e.isJsonObject() ? Optional.of(e.getAsJsonObject()) : Optional.empty();
            } catch (RuntimeException ex) {
                return Optional.empty(); // a broken file in the pack: the client can't draw it either
            }
        }).orElse(null);
    }

    private byte[] readResource(String location, String kind, String ext) {
        String[] nsPath = split(location);
        String path = nsPath[1].startsWith(kind + "/") ? nsPath[1].substring(kind.length() + 1) : nsPath[1];
        return resources.read("assets/" + nsPath[0] + "/" + kind + "/" + path + ext);
    }

    // -- block states ---------------------------------------------------------------------------------------

    /** The models drawn for a block state (the first of each random list), or null if the block has no
     *  blockstate file. */
    public List<Placed> modelsFor(String state) {
        BlockStates.Parsed parsed = BlockStates.parse(state);
        JsonObject definition = readJson("blockstates", parsed.name());
        if (definition == null) {
            return null;
        }
        List<JsonObject> out = new ArrayList<>();
        if (definition.has("variants") && definition.get("variants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : definition.getAsJsonObject("variants").entrySet()) {
                if (variantMatches(e.getKey(), parsed.props())) {
                    first(e.getValue()).ifPresent(out::add);
                    break;
                }
            }
        }
        if (definition.has("multipart") && definition.get("multipart").isJsonArray()) {
            for (JsonElement part : definition.getAsJsonArray("multipart")) {
                JsonObject p = part.getAsJsonObject();
                if (!p.has("when") || whenMatches(p.getAsJsonObject("when"), parsed.props())) {
                    first(p.get("apply")).ifPresent(out::add);
                }
            }
        }
        List<Placed> placed = new ArrayList<>();
        for (JsonObject m : out) {
            if (m.has("model")) {
                placed.add(new Placed(m.get("model").getAsString(), m.has("x") ? m.get("x").getAsInt() : 0,
                        m.has("y") ? m.get("y").getAsInt() : 0));
            }
        }
        return placed;
    }

    private static Optional<JsonObject> first(JsonElement value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value.isJsonArray()) {
            JsonArray a = value.getAsJsonArray();
            return a.isEmpty() ? Optional.empty() : Optional.of(a.get(0).getAsJsonObject());
        }
        return value.isJsonObject() ? Optional.of(value.getAsJsonObject()) : Optional.empty();
    }

    private static boolean variantMatches(String key, Map<String, String> props) {
        if (key.isEmpty() || key.equals("normal")) {
            return true;
        }
        for (String part : key.split(",")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String k = part.substring(0, eq), v = part.substring(eq + 1);
            if (props.containsKey(k) && !props.get(k).equals(v)) { // a property left out matches any value
                return false;
            }
        }
        return true;
    }

    private static boolean whenMatches(JsonObject when, Map<String, String> props) {
        if (when.has("OR")) {
            for (JsonElement w : when.getAsJsonArray("OR")) {
                if (whenMatches(w.getAsJsonObject(), props)) {
                    return true;
                }
            }
            return false;
        }
        if (when.has("AND")) {
            for (JsonElement w : when.getAsJsonArray("AND")) {
                if (!whenMatches(w.getAsJsonObject(), props)) {
                    return false;
                }
            }
            return true;
        }
        for (Map.Entry<String, JsonElement> e : when.entrySet()) {
            String value = props.get(e.getKey());
            if (value == null || !List.of(e.getValue().getAsString().toLowerCase().split("\\|")).contains(value)) {
                return false;
            }
        }
        return true;
    }

    // -- models ---------------------------------------------------------------------------------------------

    /** A model's boxes {x0, y0, z0, x1, y1, z1} (0-16 units), following the parent chain; exact if they are plain
     *  boxes (not an OBJ model's bounding box, no turned elements). */
    private record Boxes(List<double[]> boxes, boolean exact) {
    }

    private Boxes boxes(String location) {
        for (int depth = 0; location != null && depth <= 20; depth++) {
            JsonObject model = readJson("models", location);
            if (model == null) {
                return new Boxes(List.of(), true);
            }
            if (model.has("elements")) {
                List<double[]> boxes = new ArrayList<>();
                boolean exact = true;
                for (JsonElement e : model.getAsJsonArray("elements")) {
                    JsonObject el = e.getAsJsonObject();
                    JsonArray from = el.getAsJsonArray("from"), to = el.getAsJsonArray("to");
                    boxes.add(new double[] {from.get(0).getAsDouble(), from.get(1).getAsDouble(), from.get(2).getAsDouble(),
                            to.get(0).getAsDouble(), to.get(1).getAsDouble(), to.get(2).getAsDouble()});
                    JsonObject rotation = el.getAsJsonObject("rotation");
                    if (rotation != null && rotation.has("angle") && rotation.get("angle").getAsDouble() != 0) {
                        exact = false;
                    }
                }
                return new Boxes(boxes, exact);
            }
            String parent = model.has("parent") ? model.get("parent").getAsString() : null;
            if ("special-model-loader:builtin/obj".equals(parent) && model.has("model")) {
                return new Boxes(objBox(model.get("model").getAsString()), false);
            }
            location = parent;
        }
        return new Boxes(List.of(), true);
    }

    private List<double[]> objBox(String location) {
        byte[] raw = readResource(location, "models", "");
        if (raw == null) {
            return List.of();
        }
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        boolean any = false;
        for (String line : new String(raw, StandardCharsets.UTF_8).split("\\R")) {
            if (!line.startsWith("v ")) {
                continue;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 4) {
                continue;
            }
            for (int a = 0; a < 3; a++) {
                double v = Double.parseDouble(parts[a + 1]);
                lo[a] = Math.min(lo[a], v);
                hi[a] = Math.max(hi[a], v);
            }
            any = true;
        }
        if (!any) {
            return List.of();
        }
        double max = Math.max(hi[0], Math.max(hi[1], hi[2])), min = Math.min(lo[0], Math.min(lo[1], lo[2]));
        if (max <= 1.5) { // block units
            for (int a = 0; a < 3; a++) {
                lo[a] *= 16;
                hi[a] *= 16;
            }
            min *= 16;
        }
        if (min < -4) { // centred on the origin
            for (int a = 0; a < 3; a++) {
                lo[a] += 8;
                hi[a] += 8;
            }
        }
        return List.of(new double[] {lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]});
    }

    /** Boxes of everything a state draws, turned as the blockstate says; null without a blockstate file. */
    private Boxes stateBoxes(String state) {
        List<Placed> models = modelsFor(state);
        if (models == null) {
            return null;
        }
        List<double[]> boxes = new ArrayList<>();
        boolean exact = !models.isEmpty();
        for (Placed m : models) {
            Boxes b = boxes(m.model());
            exact &= b.exact();
            for (double[] box : b.boxes()) {
                boxes.add(rotateBox(box, m.x(), m.y()));
            }
        }
        return new Boxes(boxes, exact);
    }

    /** The octants (bit ox + 2*oz + 4*oy) the state's models fill at least half of, or null if the block has no
     *  blockstate file. */
    public Integer mask(String state) {
        double[] cover = coverage(state);
        if (cover == null) {
            return null;
        }
        int mask = 0;
        for (int b = 0; b < 8; b++) {
            if (cover[b] >= 0.5) {
                mask |= 1 << b;
            }
        }
        return mask;
    }

    /**
     * The state's octants if it has a clean shape: plain boxes that fill each octant almost entirely or leave it
     * almost empty, so a 4-pixel thin slab or an OBJ wedge isn't taken for a half slab or a full block. Null
     * otherwise.
     */
    public Integer cleanMask(String state) {
        return cleanMasks.computeIfAbsent(state, s -> Optional.ofNullable(computeCleanMask(s))).orElse(null);
    }

    private Integer computeCleanMask(String state) {
        Boxes boxes = stateBoxes(state);
        if (boxes == null || !boxes.exact()) {
            return null;
        }
        double[] cover = coverage(boxes.boxes());
        int mask = 0;
        for (int b = 0; b < 8; b++) {
            if (cover[b] >= 0.9) {
                mask |= 1 << b;
            } else if (cover[b] > 0.1) {
                return null;
            }
        }
        return mask;
    }

    /** How much of each octant (by bit) the state's models fill, from 0 to 1; null without a blockstate file. */
    public double[] coverage(String state) {
        Boxes boxes = stateBoxes(state);
        return boxes == null ? null : coverage(boxes.boxes());
    }

    private static double[] coverage(List<double[]> boxes) {
        double[] cover = new double[8];
        for (int oy = 0; oy < 2; oy++) {
            for (int oz = 0; oz < 2; oz++) {
                for (int ox = 0; ox < 2; ox++) {
                    int inside = 0;
                    for (int sx = 0; sx < 4; sx++) {
                        for (int sy = 0; sy < 4; sy++) {
                            for (int sz = 0; sz < 4; sz++) {
                                double px = ox * 8 + sx * 2 + 1, py = oy * 8 + sy * 2 + 1, pz = oz * 8 + sz * 2 + 1;
                                for (double[] b : boxes) {
                                    if (px >= b[0] && px <= b[3] && py >= b[1] && py <= b[4] && pz >= b[2] && pz <= b[5]) {
                                        inside++;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    cover[ox + 2 * oz + 4 * oy] = inside / 64.0;
                }
            }
        }
        return cover;
    }

    // -- textures -------------------------------------------------------------------------------------------

    /**
     * The texture the state shows towards a direction (index into {@link Octants#DX}): of the faces pointing that
     * way, the biggest, and of those the outermost. Null if the models have no such face.
     */
    public Face face(String state, int dir) {
        return faces.computeIfAbsent(state + "|" + dir, k -> Optional.ofNullable(computeFace(state, dir))).orElse(null);
    }

    private Face computeFace(String state, int dir) {
        List<Placed> models = modelsFor(state);
        if (models == null) {
            return null;
        }
        String bestTexture = null;
        boolean bestTinted = false;
        double bestArea = 0, bestDepth = -1;
        for (Placed placed : models) {
            List<JsonObject> chain = chain(placed.model());
            Map<String, String> textures = new HashMap<>();
            for (int i = chain.size() - 1; i >= 0; i--) {
                JsonObject t = chain.get(i).getAsJsonObject("textures");
                if (t != null) {
                    t.entrySet().forEach(e -> {
                        if (e.getValue().isJsonPrimitive()) {
                            textures.put(e.getKey(), e.getValue().getAsString());
                        }
                    });
                }
            }
            JsonArray elements = null;
            for (JsonObject m : chain) {
                if (m.has("elements")) {
                    elements = m.getAsJsonArray("elements");
                    break;
                }
            }
            if (elements == null) {
                continue;
            }
            for (JsonElement el : elements) {
                JsonObject e = el.getAsJsonObject();
                JsonObject faces = e.getAsJsonObject("faces");
                if (faces == null) {
                    continue;
                }
                JsonArray from = e.getAsJsonArray("from"), to = e.getAsJsonArray("to");
                double[] box = rotateBox(new double[] {from.get(0).getAsDouble(), from.get(1).getAsDouble(), from.get(2).getAsDouble(),
                        to.get(0).getAsDouble(), to.get(1).getAsDouble(), to.get(2).getAsDouble()}, placed.x(), placed.y());
                for (int f = 0; f < 6; f++) {
                    JsonObject face = faces.getAsJsonObject(FACES[f]);
                    if (face == null || !face.has("texture")) {
                        continue;
                    }
                    double[] n = rotate(NORMALS[f][0], NORMALS[f][1], NORMALS[f][2], placed.x(), placed.y());
                    if (direction(n) != dir) {
                        continue;
                    }
                    int axis = dir == 0 || dir == 1 ? 0 : dir == 4 || dir == 5 ? 1 : 2;
                    double area = 1;
                    for (int a = 0; a < 3; a++) {
                        if (a != axis) {
                            area *= box[a + 3] - box[a];
                        }
                    }
                    double depth = dir % 2 == 0 ? box[axis + 3] : 16 - box[axis];
                    String texture = resolve(face.get("texture").getAsString(), textures);
                    if (texture != null && (area > bestArea + 1e-6 || (Math.abs(area - bestArea) <= 1e-6 && depth > bestDepth))) {
                        bestArea = area;
                        bestDepth = depth;
                        bestTexture = texture;
                        bestTinted = face.has("tintindex");
                    }
                }
            }
        }
        if (bestTexture == null) {
            return null;
        }
        Stats stats = stats(bestTexture);
        int rgb = stats.rgb();
        if (rgb >= 0 && bestTinted) {
            rgb = ((rgb >> 16 & 255) * GRASS_TINT[0] / 255) << 16 | ((rgb >> 8 & 255) * GRASS_TINT[1] / 255) << 8
                    | (rgb & 255) * GRASS_TINT[2] / 255;
        }
        return new Face(bestTexture, rgb, stats.contrast());
    }

    private List<JsonObject> chain(String location) {
        List<JsonObject> chain = new ArrayList<>();
        while (location != null && chain.size() < 20) {
            JsonObject model = readJson("models", location);
            if (model == null) {
                break;
            }
            chain.add(model);
            location = model.has("parent") ? model.get("parent").getAsString() : null;
        }
        return chain;
    }

    private static String resolve(String ref, Map<String, String> textures) {
        for (int i = 0; i < 10 && ref != null && ref.startsWith("#"); i++) {
            ref = textures.get(ref.substring(1));
        }
        if (ref == null || ref.isEmpty() || ref.startsWith("#")) {
            return null;
        }
        return ref.contains(":") ? ref : "minecraft:" + ref;
    }

    /** Average colour and brightness contrast of a texture (first frame of an animation, weighted by alpha). */
    private Stats stats(String texture) {
        return colors.computeIfAbsent(texture, t -> {
            byte[] raw = readResource(t, "textures", ".png");
            if (raw == null) {
                return NO_IMAGE;
            }
            BufferedImage img;
            try {
                img = readImage(raw);
            } catch (IOException | RuntimeException ex) {
                return NO_IMAGE;
            }
            if (img == null) {
                return NO_IMAGE;
            }
            int w = img.getWidth(), h = Math.min(img.getHeight(), w);
            double r = 0, g = 0, b = 0, a = 0, l = 0, ll = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    double alpha = (argb >>> 24) / 255.0;
                    int cr = argb >> 16 & 255, cg = argb >> 8 & 255, cb = argb & 255;
                    double lum = 0.299 * cr + 0.587 * cg + 0.114 * cb;
                    r += cr * alpha;
                    g += cg * alpha;
                    b += cb * alpha;
                    l += lum * alpha;
                    ll += lum * lum * alpha;
                    a += alpha;
                }
            }
            if (a == 0) {
                return NO_IMAGE;
            }
            double mean = l / a;
            return new Stats((int) Math.round(r / a) << 16 | (int) Math.round(g / a) << 8 | (int) Math.round(b / a),
                    Math.sqrt(Math.max(0, ll / a - mean * mean)));
        });
    }

    /** The image, or null if no reader knows it or it is wider or higher than MAX_TEXTURE_SIDE, which is read from
     *  its header before any pixels. */
    private static BufferedImage readImage(byte[] raw) throws IOException {
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(raw))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if (reader.getWidth(0) > MAX_TEXTURE_SIDE || reader.getHeight(0) > MAX_TEXTURE_SIDE) {
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    // -- rotation -------------------------------------------------------------------------------------------

    /** Block model rotation: rotateYXZ(-y, -x, 0), so X turns first, then Y. Coordinates relative to the centre. */
    private static double[] rotate(double x, double y, double z, int rx, int ry) {
        for (int i = 0; i < Math.floorMod(rx / 90, 4); i++) { // -90 degrees about X: y' = z, z' = -y
            double t = y;
            y = z;
            z = -t;
        }
        for (int i = 0; i < Math.floorMod(ry / 90, 4); i++) { // -90 degrees about Y: x' = -z, z' = x
            double t = x;
            x = -z;
            z = t;
        }
        return new double[] {x, y, z};
    }

    static double[] rotateBox(double[] b, int rx, int ry) {
        double[] p = rotate(b[0] - 8, b[1] - 8, b[2] - 8, rx, ry), q = rotate(b[3] - 8, b[4] - 8, b[5] - 8, rx, ry);
        return new double[] {Math.min(p[0], q[0]) + 8, Math.min(p[1], q[1]) + 8, Math.min(p[2], q[2]) + 8,
                Math.max(p[0], q[0]) + 8, Math.max(p[1], q[1]) + 8, Math.max(p[2], q[2]) + 8};
    }

    private static int direction(double[] n) {
        for (int d = 0; d < 6; d++) {
            if (Math.abs(n[0] - NORMALS[d][0]) < 1e-6 && Math.abs(n[1] - NORMALS[d][1]) < 1e-6 && Math.abs(n[2] - NORMALS[d][2]) < 1e-6) {
                return d;
            }
        }
        return -1;
    }
}
