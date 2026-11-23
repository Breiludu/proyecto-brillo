import java.io.File
import kotlin.system.exitProcess

data class PlayerSpec(val type: String, val name: String)

data class GameConfig(var players: MutableList<PlayerSpec>) {
    var verbose = false
    var iterations = 100
    var delay = 600
}

fun main() {
    val configPath = "config.txt"
    val config = loadConfig(configPath)

    if(!(config.players.size in 2..5)) {
        fail("Game needs from 2 to 5 players.")
    }

    val wins = mutableMapOf<String, Int>()

    for (i in 0..<config.iterations) {
        val winner = Game(config).run()
        wins[winner.name] = (wins[winner.name] ?: 0) + 1
    }

    for ((name, n) in wins.entries.sortedByDescending() { it.value }) {
        val pct = 100.0 * n / config.iterations
        println("$name won $n times (${pct})")
    }
}

fun fail(msg: String): Nothing {
    println("ERROR : ${msg}")
    exitProcess(1)
}

fun loadConfig(path: String): GameConfig {
    val file = File(path)
    if(!file.exists()) {
        fail("Config file not found (${path}).")
    }

    val fileLines = file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

    // List of Players
    var players = mutableListOf<PlayerSpec>()
    fileLines
        .filter { it.startsWith("[player]") }
        .forEachIndexed { i, line ->
            val parts = line
                .substring(8)
                .split(" ")
                .filter { it != "" }
            if (parts.isEmpty()) fail("Invalid config line: '$line'")

            val type = parts[0].lowercase()
            val name = parts.getOrNull(1) ?: "Bombero$i"
            players.add(PlayerSpec(type, name))
        }

    // Other configs
    val config = GameConfig(players)
    fileLines
        .forEach { line ->
            if(line.startsWith("[player]")) {

            // Verbose
            } else if(line.startsWith("[verbose]")) {
                val selection = line.substring(9).trim().lowercase()
                config.verbose = selection.toBooleanStrictOrNull() ?: fail("Invalid boolean literal in line: '${line}'")

            // Iterations
            } else if(line.startsWith("[iterations]")) {
                val selection = line.substring(12).trim().lowercase()
                config.iterations = selection.toIntOrNull() ?: fail("Invalid number in line: '${line}'")

            // Print delay
            } else if(line.startsWith("[delay]")) {
                val selection = line.substring(7).trim().lowercase()
                config.delay = selection.toIntOrNull() ?: fail("Invalid number in line: '${line}'")

            // Fail
            } else {
                fail("Invalid config line: ${line}")
            }
        }

    return config
}

fun createPlayer(spec: PlayerSpec): Player {
    when(spec.type) {
        "human"      -> return HumanPlayer(spec.name)
        "defensive"  -> return DefensiveBot(spec.name)
        "aggressive" -> return AggressiveBot(spec.name)
        "random"     -> return RandomBot(spec.name)
        "alwaysdraw" -> return AllwaysDrawBot(spec.name)
        else         -> fail("Invalid player type '${spec.type}'")
    }
}
