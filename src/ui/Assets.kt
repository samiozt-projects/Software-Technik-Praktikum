package ui

import korlibs.image.bitmap.Bitmap32
import korlibs.image.color.Colors
import korlibs.image.font.Font
import korlibs.image.font.readFont
import korlibs.image.format.readBitmap
import korlibs.io.file.std.resourcesVfs

/**
 * Central asset manager for the KorGE simulation.
 * All shared sprites are preloaded at startup to ensure smooth scene transitions.
 */
object Assets {
    /** Arrow pointing to the start node in the graph. */
    lateinit var arrowStart: Bitmap32
    /** Arrow pointing to the end node in the graph. */
    lateinit var arrowEnd: Bitmap32
    /** Button to open the main menu. */
    lateinit var menu: Bitmap32
    /** Save game button. */
    lateinit var save: Bitmap32
    /** Load game button. */
    lateinit var loadLevel: Bitmap32
    /** Settings (gear) button. */
    lateinit var gear: Bitmap32
    /** Close / back button. */
    lateinit var close: Bitmap32
    /** Delete / remove button. */
    lateinit var delete: Bitmap32
    /** Car sprite shown when pause mode is active. */
    lateinit var carPause: Bitmap32
    /** Generic car sprite. */
    lateinit var car: Bitmap32
    /** Truck sprite. */
    lateinit var truck: Bitmap32
    /** Motorcycle sprite. */
    lateinit var motorcycle: Bitmap32
    /** Button to start the simulation. */
    lateinit var startSimulation: Bitmap32
    /** Icon used for construction sites. */
    lateinit var construction: Bitmap32
    /** Icon used for the multi-table/server menu button. */
    lateinit var globus: Bitmap32
    /** Weather icon shown for dry or sunny conditions. */
    lateinit var sun: Bitmap32
    /** Weather icon shown for wet or rainy conditions. */
    lateinit var cloud: Bitmap32
    /** Weather icon shown for snowy conditions. */
    lateinit var snowflake: Bitmap32
    /** Background landscape used by dry or sunny levels. */
    lateinit var sunnyLandscape: Bitmap32
    /** Background landscape used by wet or rainy levels. */
    lateinit var wetLandscape: Bitmap32
    /** Background landscape used by snowy levels. */
    lateinit var snowyLandscape: Bitmap32
    /** Font used for single-digit numbers in vehicle counters. */
    lateinit var bitcountSingle: Font

    /** Guards [loadAll] so the one-time decode of all sprites and the font runs only once. */
    private var loaded = false

    /**
     * Loads all required assets from the resources folder.
     * Falls back to a magenta placeholder if an asset is missing.
     *
     * Idempotent: after the first successful run, further calls return immediately. This keeps
     * repeated calls (e.g. on every scene entry) from re-decoding the same PNGs and font.
     */
    suspend fun loadAll() {
        if (loaded) return

        arrowStart = loadSprite("images/source.png")
        arrowEnd = loadSprite("images/destination.png")
        menu = loadSprite("images/menu.png")
        save = loadSprite("images/save.png")
        loadLevel = loadSprite("images/load_level.png")
        gear = loadSprite("images/gear.png")
        close = loadSprite("images/close.png")
        delete = loadSprite("images/delete.png")
        carPause = loadSprite("images/car_pause.png")
        car = loadSprite("images/car.png")
        truck = loadSprite("images/truck.png")
        motorcycle = loadSprite("images/motorcycle.png")
        startSimulation = loadSprite("images/start_simulation.png")
        construction = loadSprite("images/construction.png")
        globus = loadSprite("images/globus.png")
        sun = loadSprite("images/sun.png")
        cloud = loadSprite("images/cloud.png")
        snowflake = loadSprite("images/snowflake.png")
        sunnyLandscape = loadSprite("images/landscape1.png")
        wetLandscape = loadSprite("images/landscape2.png")
        snowyLandscape = loadSprite("images/landscape3.png")

        bitcountSingle = loadFont("fonts/Bitcount_Single/static/BitcountSingle-Regular.ttf")

        loaded = true
    }

    /**
     * Loads a sprite from the given path. If loading fails, returns a magenta placeholder bitmap.
     * 
     * @param path The path to the sprite.
     * @return The loaded sprite.
     */
    private suspend fun loadSprite(path: String): Bitmap32 {
        return try {
            resourcesVfs[path].readBitmap().toBMP32().premultiplied()
        } catch (e: Exception) {
            println("Warning: Failed to load asset $path. Using placeholder. Error: ${e.message}")
            Bitmap32(64, 64, premultiplied = true).also { bmp ->
                bmp.fill(Colors.MAGENTA)
            }
        }
    }

    /**
     * Loads a font from the given path. If loading fails, returns the default font.
     * 
     * @param path The path to the font.
     * @return The loaded font.
     */
    private suspend fun loadFont(path: String): Font {
        return try {
            resourcesVfs[path].readFont()
        } catch (e: Exception) {
            println("Warning: Failed to load font $path. Using default font. Error: ${e.message}")
            korlibs.image.font.DefaultTtfFont
        }
    }

    /**
     * A thread-safe/local cache storing decoded vehicle images.
     * Key: The file path String (e.g. "images/truck.png")
     * Value: The fully loaded and ready-to-render Bitmap32 asset.
     */
    private val vehicleGraphics: MutableMap<String, Bitmap32> = mutableMapOf()

    /**
     * Preloads all vehicle graphics referenced by the given vehicle types.
     * Call this once after the level has been loaded so scenes can synchronously
     * fetch bitmaps via getVehicleGraphic(ref).
     * 
     * @param vehicleTypes The list of vehicle configurations defined in the loaded level.
     */
    suspend fun preloadVehicleGraphics(vehicleTypes: List<domain.VehicleType>) {
        for (vt in vehicleTypes) {
            val ref = vt.graphicRef
            // Optimization: Only attempt to load if the path exists AND
            // we haven't yet cached this exact graphic previously.
            if (ref.isNotBlank() && !vehicleGraphics.containsKey(ref)) {
                try {
                    // Reuses the pre-existing private asset loader function in Assets.kt
                    val bmp = loadSprite(ref)
                    // Commit the loaded texture to the cache map
                    vehicleGraphics[ref] = bmp
                } catch (e: Exception) {
                    println("Warning: Failed to preload vehicle graphic asset from path: $ref. Error: ${e.message}")
                }
            }
        }
    }

    /**
     * Returns a preloaded vehicle graphic by reference.
     * If the ref is blank or not found, falls back to the generic car sprite.
     * This is synchronous and safe to call from rendering code.
     *
     * @param ref The reference to the vehicle graphic.
     * @return The preloaded vehicle graphic.
     */
    fun getVehicleGraphic(ref: String): Bitmap32 {
        // car is here a placeholder because no ref is provided.
        if (ref.isBlank()) return car
        // else use ref to look up the cached asset
        return vehicleGraphics[ref] ?: car
    }

}
