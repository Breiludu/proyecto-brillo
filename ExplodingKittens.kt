// Exploding Kittens para consola en Kotlin
// Compilar:  kotlinc ExplodingKittens.kt -include-runtime -d ExplodingKittens.jar
// Ejecutar:  java -jar ExplodingKittens.jar

import kotlin.random.Random
import kotlin.system.exitProcess

const val BOT_DELAY_MS = 600L

// ---------------------------------------------------------------- Cartas

enum class CardType(val label: String, val isCat: Boolean = false) {
    EXPLODING_KITTEN("Exploding Kitten"),
    DEFUSE("Defuse"),
    ATTACK("Attack"),
    SKIP("Skip"),
    FAVOR("Favor"),
    SHUFFLE("Shuffle"),
    SEE_THE_FUTURE("See the Future"),
    NOPE("Nope"),
    TACOCAT("Tacocat", isCat = true),
    CATTERMELON("Cattermelon", isCat = true),
    BEARD_CAT("Beard Cat", isCat = true),
    HAIRY_POTATO_CAT("Hairy Potato Cat", isCat = true),
    RAINBOW_RALPHING_CAT("Rainbow-Ralphing Cat", isCat = true);

    val description: String
        get() = when (this) {
            EXPLODING_KITTEN -> "Si la robas sin Defuse, explotas"
            DEFUSE -> "Te salva de un Exploding Kitten"
            ATTACK -> "Terminas sin robar; el siguiente juega 2 turnos"
            SKIP -> "Terminas un turno sin robar"
            FAVOR -> "Otro jugador te da una carta a su elección"
            SHUFFLE -> "Baraja el mazo"
            SEE_THE_FUTURE -> "Mira las 3 primeras cartas del mazo"
            NOPE -> "Cancela una acción (solo como respuesta)"
            else -> "Juega una pareja para robar una carta al azar"
        }
}

class Deck {
    private val cards = mutableListOf<CardType>()

    val size get() = cards.size
    fun draw(): CardType = cards.removeAt(0)
    fun peek(n: Int): List<CardType> = cards.take(n)
    fun shuffle() = cards.shuffle()
    fun insert(card: CardType, index: Int) = cards.add(index.coerceIn(0, cards.size), card)
    fun addAll(newCards: Collection<CardType>) { cards.addAll(newCards) }
}

// ---------------------------------------------------------------- Acciones

sealed class Action {
    data object Draw : Action()
    data class PlayCard(val card: CardType) : Action()
    data class PlayPair(val card: CardType) : Action()
}

enum class TurnResult { DREW, SKIPPED, ATTACKED, EXPLODED }

// ---------------------------------------------------------------- Jugadores

abstract class Player(val name: String) {
    val hand = mutableListOf<CardType>()
    var alive = true
    open val isHuman = false

    fun has(card: CardType) = card in hand
    fun count(card: CardType) = hand.count { it == card }

    open fun startTurn() {}
    open fun onDeckShuffled() {}
    open fun seeFuture(cards: List<CardType>) {}

    abstract fun chooseAction(game: Game): Action
    abstract fun wantsToNope(description: String, actor: Player, target: Player?, actionIsNoped: Boolean): Boolean
    abstract fun choosePositionForKitten(deckSize: Int): Int
    abstract fun chooseCardToGive(to: Player): CardType
    abstract fun chooseTarget(candidates: List<Player>): Player
}

class HumanPlayer(name: String) : Player(name) {
    override val isHuman = true

    // TODO: Mostrar solo acciones posibles
    override fun chooseAction(game: Game): Action {
        while (true) {
            println("\nMano de $name:")
            hand.forEachIndexed { i, c -> println("  ${i + 1}. ${c.label.padEnd(22)} (${c.description})") }
            println("  0. Robar carta y terminar turno")
            val choice = readInt("Elige una opción: ", 0..hand.size)
            if (choice == 0) return Action.Draw

            val card = hand[choice - 1]
            when {
                card == CardType.DEFUSE ->
                    println("El Defuse se usa automáticamente al robar un Exploding Kitten.")
                card == CardType.NOPE ->
                    println("El Nope solo se juega en respuesta a la acción de otro jugador.")
                card.isCat && count(card) < 2 ->
                    println("Necesitas dos ${card.label} para jugarlas en pareja.")
                card.isCat -> return Action.PlayPair(card)
                else -> return Action.PlayCard(card)
            }
        }
    }

    // --- Reacciones ---

    override fun wantsToNope(description: String, actor: Player, target: Player?, actionIsNoped: Boolean): Boolean {
        val answer = readLineOrExit("[$name] $description. ¿Juegas Nope? (s/n): ")
        return answer.trim().lowercase().startsWith("s")
    }

    override fun choosePositionForKitten(deckSize: Int): Int =
        readInt("[$name] ¿Dónde escondes el Exploding Kitten? (0 = arriba del todo, $deckSize = abajo del todo): ", 0..deckSize)

    override fun chooseCardToGive(to: Player): CardType {
        println("\n[$name] ${to.name} te ha pedido un Favor. Elige qué carta darle:")
        hand.forEachIndexed { i, c -> println("  ${i + 1}. ${c.label}") }
        return hand[readInt("Carta: ", 1..hand.size) - 1]
    }

    override fun chooseTarget(candidates: List<Player>): Player {
        println("Elige un jugador:")
        candidates.forEachIndexed { i, p -> println("  ${i + 1}. ${p.name} (${p.hand.size} cartas)") }
        return candidates[readInt("Jugador: ", 1..candidates.size) - 1]
    }

    override fun seeFuture(cards: List<CardType>) {
        println("\nLas próximas cartas del mazo son:")
        cards.forEachIndexed { i, c -> println("  ${i + 1}. ${c.label}") }
    }
}

class BotPlayer(name: String) : Player(name) {
    private var kittenOnTop = false
    private var lookedThisTurn = false

    override fun startTurn() {
        kittenOnTop = false
        lookedThisTurn = false
    }

    override fun onDeckShuffled() { kittenOnTop = false }

    override fun seeFuture(cards: List<CardType>) {
        kittenOnTop = cards.firstOrNull() == CardType.EXPLODING_KITTEN
    }

    override fun chooseAction(game: Game): Action {
        // Si sabe que arriba hay un gatito, intenta escapar
        if (kittenOnTop) {
            listOf(CardType.ATTACK, CardType.SKIP, CardType.SHUFFLE)
                .firstOrNull { has(it) }
                ?.let { return Action.PlayCard(it) }
        }
        // Sin Defuse, mirar el futuro es prudente
        if (!lookedThisTurn && !has(CardType.DEFUSE) && has(CardType.SEE_THE_FUTURE)) {
            lookedThisTurn = true
            return Action.PlayCard(CardType.SEE_THE_FUTURE)
        }
        if (game.hasTargets(this)) {
            val pair = hand.filter { it.isCat }.groupingBy { it }.eachCount()
                .entries.firstOrNull { it.value >= 2 }?.key
            if (pair != null && Random.nextDouble() < 0.5) return Action.PlayPair(pair)
            if (has(CardType.FAVOR) && Random.nextDouble() < 0.4) return Action.PlayCard(CardType.FAVOR)
        }
        // Sin Defuse y sin información, a veces evita robar
        if (!has(CardType.DEFUSE) && !lookedThisTurn && Random.nextDouble() < 0.3) {
            listOf(CardType.SKIP, CardType.ATTACK).firstOrNull { has(it) }
                ?.let { return Action.PlayCard(it) }
        }
        return Action.Draw
    }

    override fun wantsToNope(description: String, actor: Player, target: Player?, actionIsNoped: Boolean): Boolean {
        val chance = when {
            actionIsNoped && actor == this -> 0.8        // le cancelaron su acción: contraataca
            !actionIsNoped && target == this -> 0.75     // la acción va contra él
            !actionIsNoped && actor != this -> 0.1
            else -> 0.0
        }
        return Random.nextDouble() < chance
    }

    override fun choosePositionForKitten(deckSize: Int): Int = Random.nextInt(0, deckSize + 1)

    override fun chooseCardToGive(to: Player): CardType {
        val priority = listOf(
            CardType.TACOCAT, CardType.CATTERMELON, CardType.BEARD_CAT, CardType.HAIRY_POTATO_CAT,
            CardType.RAINBOW_RALPHING_CAT, CardType.SHUFFLE, CardType.SEE_THE_FUTURE, CardType.FAVOR,
            CardType.SKIP, CardType.ATTACK, CardType.NOPE, CardType.DEFUSE
        )
        return hand.minBy { priority.indexOf(it) }
    }

    override fun chooseTarget(candidates: List<Player>): Player = candidates.maxBy { it.hand.size }
}

// ---------------------------------------------------------------- Partida

class Game(private val players: List<Player>) {
    private val deck = Deck()
    private var current = 0

    fun alivePlayers() = players.filter { it.alive }
    fun hasTargets(p: Player) = targetsFor(p).isNotEmpty()
    private fun targetsFor(p: Player) = alivePlayers().filter { it != p && it.hand.isNotEmpty() }

    // Meter todas las cartas en el mazo
    private fun setup() {
        val base = mutableListOf<CardType>()
        fun add(card: CardType, n: Int) = repeat(n) { base.add(card) }
        add(CardType.ATTACK, 4); add(CardType.SKIP, 4); add(CardType.FAVOR, 4)
        add(CardType.SHUFFLE, 4); add(CardType.NOPE, 5); add(CardType.SEE_THE_FUTURE, 5)
        CardType.entries.filter { it.isCat }.forEach { add(it, 4) }
        base.shuffle()

        // Cada jugador: 1 Defuse + 7 cartas
        players.forEach { p ->
            p.hand.add(CardType.DEFUSE)
            repeat(7) { p.hand.add(base.removeAt(0)) }
        }
        deck.addAll(base)
        // Reglas originales: 6 Defuse en total, como mucho 2 vuelven al mazo
        repeat(minOf(2, 6 - players.size)) { deck.insert(CardType.DEFUSE, 0) }
        // Gatitos explosivos: uno menos que jugadores
        repeat(players.size - 1) { deck.insert(CardType.EXPLODING_KITTEN, 0) }
        deck.shuffle()
    }

    // Game loop
    fun run() {
        setup()
        var turnsLeft = 1
        
        while (alivePlayers().size > 1) {
            val player = players[current]

            when (playTurn(player, turnsLeft)) {
                TurnResult.DREW, TurnResult.SKIPPED -> {
                    turnsLeft--
                    if (turnsLeft == 0) { advance(); turnsLeft = 1 }
                }

                TurnResult.ATTACKED -> { advance(); turnsLeft = 2 }

                TurnResult.EXPLODED -> { advance(); turnsLeft = 1 }
            }
        }

        println("\n==============================================")
        println("  ¡${alivePlayers().first().name} gana la partida!")
        println("==============================================")
    }

    // Siguiente jugador (vivo)
    private fun advance() {
        do { current = (current + 1) % players.size } while (!players[current].alive)
    }

    private fun printStatus(player: Player, turnsLeft: Int) {
        println("\n----------------------------------------------")
        val extra = if (turnsLeft > 1) " (le quedan $turnsLeft turnos)" else ""
        println("Turno de ${player.name}$extra")
        println("Cartas en el mazo: ${deck.size}")
        alivePlayers().forEach { println("  ${it.name}: ${it.hand.size} cartas") }
    }

    // ??
    private fun announce(player: Player, msg: String) {
        println(msg)
        if (!player.isHuman) Thread.sleep(BOT_DELAY_MS)
    }

    private fun playTurn(player: Player, turnsLeft: Int): TurnResult {
        printStatus(player, turnsLeft)
        player.startTurn()

        while (true) {
            when (val action = player.chooseAction(this)) {
                is Action.Draw -> return drawCard(player)

                is Action.PlayPair -> {
                    val targets = targetsFor(player)
                    if (targets.isEmpty()) { println("No hay nadie a quien robar."); continue }
                    val target = player.chooseTarget(targets)
                    repeat(2) { player.hand.remove(action.card) }
                    announce(player, "${player.name} juega una pareja de ${action.card.label} contra ${target.name}.")
                    if (isNoped(player, "${player.name} quiere robarle una carta a ${target.name}", target)) continue
                    val stolen = target.hand.removeAt(Random.nextInt(target.hand.size))
                    player.hand.add(stolen)
                    announce(player, "${player.name} le roba una carta a ${target.name}.")
                    if (player.isHuman) println("Has robado: ${stolen.label}")
                    if (target.isHuman) println("[${target.name}] Te han robado: ${stolen.label}")
                }

                is Action.PlayCard -> {
                    val card = action.card
                    var target: Player? = null
                    if (card == CardType.FAVOR) {
                        val targets = targetsFor(player)
                        if (targets.isEmpty()) { println("No hay nadie a quien pedir un favor."); continue }
                        target = player.chooseTarget(targets)
                    }
                    player.hand.remove(card)
                    val targetText = target?.let { " a ${it.name}" } ?: ""
                    announce(player, "${player.name} juega ${card.label}$targetText.")

                    val next = nextAlivePlayer(player)
                    val nopeTarget = if (card == CardType.ATTACK) next else target
                    if (isNoped(player, "${player.name} ha jugado ${card.label}$targetText", nopeTarget)) continue

                    when (card) {
                        CardType.SKIP -> return TurnResult.SKIPPED
                        CardType.ATTACK -> return TurnResult.ATTACKED
                        CardType.SHUFFLE -> {
                            deck.shuffle()
                            players.forEach { it.onDeckShuffled() }
                            announce(player, "El mazo ha sido barajado.")
                        }
                        CardType.SEE_THE_FUTURE -> player.seeFuture(deck.peek(3))
                        CardType.FAVOR -> {
                            val t = target!!
                            val given = t.chooseCardToGive(player)
                            t.hand.remove(given)
                            player.hand.add(given)
                            announce(player, "${t.name} le da una carta a ${player.name}.")
                            if (player.isHuman) println("Has recibido: ${given.label}")
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun nextAlivePlayer(p: Player): Player {
        var i = players.indexOf(p)
        do { i = (i + 1) % players.size } while (!players[i].alive)
        return players[i]
    }

    /** Ronda de Nopes: cada Nope puede ser cancelado por otro Nope. */
    private fun isNoped(actor: Player, description: String, target: Player?): Boolean {
        var noped = false
        var last = actor
        while (true) {
            val desc = if (noped) "${last.name} ha jugado Nope sobre: $description" else description
            val responder = playersInOrderAfter(last)
                .filter { it.has(CardType.NOPE) }
                .firstOrNull { it.wantsToNope(desc, actor, target, noped) }
                ?: break
            responder.hand.remove(CardType.NOPE)
            noped = !noped
            last = responder
            announce(responder, "¡${responder.name} juega NOPE!")
        }
        if (noped) println("La acción queda cancelada.")
        return noped
    }

    private fun playersInOrderAfter(p: Player): List<Player> {
        val start = players.indexOf(p)
        return (1 until players.size).map { players[(start + it) % players.size] }.filter { it.alive }
    }

    private fun drawCard(player: Player): TurnResult {
        val card = deck.draw()
        if (card != CardType.EXPLODING_KITTEN) {
            player.hand.add(card)
            announce(player, "${player.name} roba una carta.")
            if (player.isHuman) println("Has robado: ${card.label}")
            return TurnResult.DREW
        }

        announce(player, "¡¡${player.name} ha robado un EXPLODING KITTEN!!")
        if (player.has(CardType.DEFUSE)) {
            player.hand.remove(CardType.DEFUSE)
            announce(player, "${player.name} usa un Defuse y desactiva al gatito.")
            deck.insert(CardType.EXPLODING_KITTEN, player.choosePositionForKitten(deck.size))
            players.forEach { it.onDeckShuffled() } // el mazo ha cambiado
            announce(player, "${player.name} vuelve a esconder el gatito en el mazo...")
            return TurnResult.DREW
        }

        announce(player, "BOOM. ${player.name} ha explotado y queda eliminado.")
        player.alive = false
        player.hand.clear()
        return TurnResult.EXPLODED
    }
}

// ---------------------------------------------------------------- Entrada

fun readLineOrExit(prompt: String): String {
    print(prompt)
    return readlnOrNull() ?: exitProcess(0)
}

fun readInt(prompt: String, range: IntRange): Int {
    while (true) {
        val n = readLineOrExit(prompt).trim().toIntOrNull()
        if (n != null && n in range) return n
        println("Introduce un número entre ${range.first} y ${range.last}.")
    }
}

fun main() {
    println("==============================================")
    println("              EXPLODING KITTENS")
    println("==============================================")

    val humans = readInt("¿Cuántos jugadores humanos? (0-5): ", 0..5)
    val minBots = maxOf(0, 2 - humans)
    val maxBots = 5 - humans
    val bots = if (maxBots == 0) 0 else readInt("¿Cuántos bots? ($minBots-$maxBots): ", minBots..maxBots)

    val players = mutableListOf<Player>()
    for (i in 1..humans) {
        val name = readLineOrExit("Nombre del jugador $i: ").trim().ifEmpty { "Jugador $i" }
        players.add(HumanPlayer(name))
    }
    for (i in 1..bots) players.add(BotPlayer("Bot $i"))

    Game(players.shuffled()).run()
}
