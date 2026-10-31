package com.canopyguard.engine;

import com.canopyguard.domain.FireCell;
import com.canopyguard.domain.FireCell.FireState;

import java.util.*;

public final class WildfireEngine {
    public record Settings(int width, int height, long seed, double windDegrees, double windKph,
                           double humidityPercent, double spreadScale) {
        public Settings {
            if (width < 3 || height < 3 || !Double.isFinite(windDegrees) || !Double.isFinite(windKph)
                    || !Double.isFinite(humidityPercent) || !Double.isFinite(spreadScale)
                    || windKph < 0 || humidityPercent < 0 || humidityPercent > 100 || spreadScale <= 0)
                throw new IllegalArgumentException("Invalid simulation settings");
        }
    }
    public record Snapshot(int tick, int width, int height, List<FireCell> cells, double windDegrees,
                           double windKph, double humidityPercent) {
        public Snapshot { cells = List.copyOf(cells); }
        public FireCell cell(int x, int y) { return cells.get(y * width + x); }
        public long burningCount() { return cells.stream().filter(c -> c.state() == FireState.BURNING).count(); }
        public long burnedCount() { return cells.stream().filter(c -> c.state() == FireState.BURNED).count(); }
    }

    private final Settings settings;
    private final double[][] fuel;
    private final double[][] moisture;
    private final double[][] slope;
    private final FireState[][] state;
    private final double[][] intensity;
    private double[][] smoke;
    private int tick;

    public WildfireEngine(Settings settings, int ignitionX, int ignitionY) {
        this.settings = Objects.requireNonNull(settings);
        if (ignitionX < 0 || ignitionX >= settings.width() || ignitionY < 0 || ignitionY >= settings.height())
            throw new IllegalArgumentException("Ignition is outside the grid");
        fuel = new double[settings.height()][settings.width()];
        moisture = new double[settings.height()][settings.width()];
        slope = new double[settings.height()][settings.width()];
        state = new FireState[settings.height()][settings.width()];
        intensity = new double[settings.height()][settings.width()];
        smoke = new double[settings.height()][settings.width()];
        Random random = new Random(settings.seed());
        for (int y = 0; y < settings.height(); y++) for (int x = 0; x < settings.width(); x++) {
            fuel[y][x] = random.nextDouble() < 0.08 ? 0 : 0.35 + random.nextDouble() * 0.65;
            moisture[y][x] = 0.15 + random.nextDouble() * 0.65;
            slope[y][x] = random.nextDouble() * 0.35;
            state[y][x] = FireState.UNBURNED;
        }
        fuel[ignitionY][ignitionX] = Math.max(0.7, fuel[ignitionY][ignitionX]);
        state[ignitionY][ignitionX] = FireState.BURNING;
        intensity[ignitionY][ignitionX] = 0.85;
        tick = 0;
    }

    public static WildfireEngine restore(Settings settings, Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (settings.width() != snapshot.width() || settings.height() != snapshot.height()
                || snapshot.cells().size() != settings.width() * settings.height() || snapshot.tick() < 0)
            throw new IllegalArgumentException("Saved simulation dimensions or tick are invalid");
        WildfireEngine restored = new WildfireEngine(settings, 0, 0);
        boolean[][] seen = new boolean[settings.height()][settings.width()];
        for (FireCell cell : snapshot.cells()) {
            if (cell.x() < 0 || cell.x() >= settings.width() || cell.y() < 0 || cell.y() >= settings.height()
                    || cell.fuel() < 0 || cell.fuel() > 1 || cell.moisture() < 0 || cell.moisture() > 1
                    || cell.intensity() < 0 || cell.intensity() > 1 || cell.smoke() < 0 || cell.smoke() > 1
                    || !Double.isFinite(cell.slope()) || cell.slope() < 0 || cell.slope() > 1
                    || seen[cell.y()][cell.x()])
                throw new IllegalArgumentException("Saved simulation contains invalid cell values");
            seen[cell.y()][cell.x()] = true;
            restored.fuel[cell.y()][cell.x()] = cell.fuel();
            restored.moisture[cell.y()][cell.x()] = cell.moisture();
            restored.slope[cell.y()][cell.x()] = cell.slope();
            restored.state[cell.y()][cell.x()] = Objects.requireNonNull(cell.state());
            restored.intensity[cell.y()][cell.x()] = cell.intensity();
            restored.smoke[cell.y()][cell.x()] = cell.smoke();
        }
        for (boolean[] row : seen) for (boolean cellSeen : row)
            if (!cellSeen) throw new IllegalArgumentException("Saved simulation has missing grid cells");
        restored.tick = snapshot.tick();
        restored.changeWind(snapshot.windDegrees(), snapshot.windKph());
        return restored;
    }

    public synchronized Snapshot advance() {
        double[][] nextSmoke = new double[settings.height()][settings.width()];
        for (int y = 0; y < settings.height(); y++) for (int x = 0; x < settings.width(); x++) {
            double source = intensity[y][x] * 0.62;
            if (source > 0) {
                int dx = (int) Math.round(Math.sin(Math.toRadians(windDegrees() + 180)));
                int dy = (int) Math.round(-Math.cos(Math.toRadians(windDegrees() + 180)));
                deposit(nextSmoke, x + dx, y + dy, source * (0.25 + windKph() / 120.0));
                deposit(nextSmoke, x, y, source * 0.3);
                deposit(nextSmoke, x - dx, y - dy, source * 0.12);
            }
        }
        for (int y = 0; y < settings.height(); y++) for (int x = 0; x < settings.width(); x++)
            nextSmoke[y][x] = Math.min(1, nextSmoke[y][x] + smoke[y][x] * 0.42);

        FireState[][] next = new FireState[settings.height()][settings.width()];
        double[][] nextIntensity = new double[settings.height()][settings.width()];
        for (int y = 0; y < settings.height(); y++) for (int x = 0; x < settings.width(); x++) {
            FireState current = state[y][x];
            if (current == FireState.BURNING) {
                next[y][x] = FireState.BURNED;
                continue;
            }
            if (current == FireState.BURNED || fuel[y][x] <= 0) {
                next[y][x] = current;
                continue;
            }
            double probability = 0;
            for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) {
                if (ox == 0 && oy == 0) continue;
                int nx = x + ox, ny = y + oy;
                if (!inside(nx, ny) || state[ny][nx] != FireState.BURNING) continue;
                double distance = Math.hypot(ox, oy);
                double windAlignment = 0;
                if (windKph() > 0 && (ox != 0 || oy != 0)) {
                    double windX = Math.sin(Math.toRadians(windDegrees() + 180));
                    double windY = -Math.cos(Math.toRadians(windDegrees() + 180));
                    windAlignment = (ox * windX + oy * windY) / distance;
                }
                double dryness = 1 - moisture[y][x];
                double windFactor = Math.max(0.12, 1 + windAlignment * Math.min(1.4, windKph() / 35.0));
                double slopeFactor = 1 + Math.max(-0.2, (slope[ny][nx] - slope[y][x]) * 2.2);
                double chance = settings.spreadScale() * fuel[y][x] * dryness
                        * (1 - settings.humidityPercent() / 145.0) * windFactor * slopeFactor / (distance * 1.9);
                probability = 1 - (1 - probability) * (1 - Math.max(0, Math.min(0.95, chance)));
            }
            if (probability > 0) {
                next[y][x] = FireState.HEATING;
                if (probability >= 0.18 + moisture[y][x] * 0.2 && moisture[y][x] < 0.84) {
                    next[y][x] = FireState.BURNING;
                    nextIntensity[y][x] = Math.min(1, fuel[y][x] * (1 - moisture[y][x]) * 1.5);
                }
            } else next[y][x] = current == FireState.HEATING ? FireState.UNBURNED : current;
        }
        stateReplace(next, nextIntensity);
        smoke = nextSmoke;
        tick++;
        return snapshot();
    }

    public synchronized Snapshot snapshot() {
        List<FireCell> cells = new ArrayList<>(settings.width() * settings.height());
        for (int y = 0; y < settings.height(); y++) for (int x = 0; x < settings.width(); x++)
            cells.add(new FireCell(x, y, fuel[y][x], moisture[y][x], slope[y][x], state[y][x], intensity[y][x], smoke[y][x]));
        return new Snapshot(tick, settings.width(), settings.height(), cells, windDegrees(), windKph(), settings.humidityPercent());
    }

    public synchronized void changeWind(double degrees, double speedKph) {
        if (!Double.isFinite(speedKph) || speedKph < 0 || speedKph > 160 || !Double.isFinite(degrees))
            throw new IllegalArgumentException("Invalid wind observation");
        // Settings are immutable; wind changes are represented by a replacement engine setting below.
        currentWindDegrees = ((degrees % 360) + 360) % 360;
        currentWindKph = speedKph;
    }

    private double currentWindDegrees = Double.NaN;
    private double currentWindKph = Double.NaN;
    private double windDegrees() { return Double.isNaN(currentWindDegrees) ? settings.windDegrees() : currentWindDegrees; }
    private double windKph() { return Double.isNaN(currentWindKph) ? settings.windKph() : currentWindKph; }

    private void stateReplace(FireState[][] next, double[][] nextIntensity) {
        for (int y = 0; y < settings.height(); y++) for (int x = 0; x < settings.width(); x++) {
            state[y][x] = next[y][x];
            intensity[y][x] = nextIntensity[y][x];
        }
    }
    private boolean inside(int x, int y) { return x >= 0 && x < settings.width() && y >= 0 && y < settings.height(); }
    private void deposit(double[][] target, int x, int y, double value) {
        if (inside(x, y)) target[y][x] += value;
    }
}
