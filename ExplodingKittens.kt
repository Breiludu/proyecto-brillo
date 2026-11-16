import kotlin.random.Random
import kotlin.system.exitProcess

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

    fun getDesc(): String {
        when(this) {
            EXPLODING_KITTEN -> return "Si la robas sin Defuse, explotas"
            DEFUSE           -> return "Te salva de un Exploding Kitten"
            ATTACK           -> return "Terminas sin robar; el siguiente juega 2 turnos"
            SKIP             -> return "Terminas un turno sin robar"
            FAVOR            -> return "Otro jugador te da una carta a su elección"
            SHUFFLE          -> return "Baraja el mazo"
            SEE_THE_FUTURE   -> return "Mira las 3 primeras cartas del mazo"
            NOPE             -> return "Cancela una acción (solo como respuesta)"
            else             -> return "Juega una pareja para robar una carta al azar"
            // TODO: Añadir desc para tres y cinco cartas de gato
        }
    }
}

class Deck {
    private val cards = mutableListOf<CardType>()

    fun getSize(): Int = cards.size
    fun draw(): CardType = cards.removeAt(0)
    fun peek(n: Int): List<CardType> = cards.take(n)
    fun shuffle() = cards.shuffle()
    fun insert(card: CardType, index: Int) = cards.add(index.coerceIn(0, cards.size), card)
    fun addAll(newCards: List<CardType>) { cards.addAll(newCards) }
}

// ---------------------------------------------------------------- Acciones

enum class ActionType {
    DRAW,
    PLAY_CARD,
    PLAY_KITTENS;
}

data class Action(
    val actor: Player,
    val type: ActionType,

    // Played card / Requested card (if played kittens)
    val card: CardType? = null,
    // Kittens played
    val kittens: List<CardType>? = null,
    // Target player of the action
    val target: Player? = null
);
 
// TODO: Use the Action played instead of returning this (maybe not possible)
enum class TurnResult {
    DREW,
    SKIPPED,
    ATTACKED,
    EXPLODED;
}

// ---------------------------------------------------------------- Jugadores

abstract class Player(val name: String) {
    val hand = mutableListOf<CardType>()
    var alive = true
    abstract val isHuman: Boolean

    fun has(card: CardType) = card in hand
    fun count(card: CardType) = hand.count { it == card }

    abstract fun act(): Action
    abstract fun nopes(action: Action, alreadyNoped: Boolean): Boolean
    // Action results
    abstract fun favor(action: Action): CardType
    abstract fun chooseCard(discardPile: List<CardType>): CardType
    abstract fun explodingKittenPosition(): Int
    abstract fun sawFuture(cards: List<CardType>)
}

class AllwaysDrawBot(name: String) : Player(name) {
    override val isHuman = false

    override fun act(): Action { return Action(this, ActionType.DRAW) }

    override fun nopes(action: Action, alreadyNoped: Boolean): Boolean { return false }

    override fun favor(action: Action): CardType { return hand[Random.nextInt(hand.size)] }

    override fun chooseCard(discardPile: List<CardType>): CardType {
        return discardPile[Random.nextInt(discardPile.size)]
    }

    override fun explodingKittenPosition(): Int { return 0 }

    override fun sawFuture(cards: List<CardType>) { }
}

// ---------------------------------------------------------------- Juego

class Game(private val players: List<Player>) {
    private val deck: Deck
    // TODO: handle discard pile
    private val discardPile = mutableListOf<CardType>()
    private var playerTurn: Int

    private fun alivePlayers(): List<Player> = players.filter { it.alive }
    private fun targetsFor(p: Player) = alivePlayers().filter { it != p && it.hand.isNotEmpty() }

    init {
        deck = Deck()
        playerTurn = 0

        // Inicializar mazo y manos de jugadores
        val fullDeck = mutableListOf<CardType>()
        fun add(card: CardType, n: Int) = repeat(n) { fullDeck.add(card) }

        add(CardType.ATTACK, 4);    add(CardType.SKIP, 4);
        add(CardType.FAVOR, 4);     add(CardType.SHUFFLE, 4);
        add(CardType.NOPE, 5);      add(CardType.SEE_THE_FUTURE, 5);

        add(CardType.TACOCAT, 4);   add(CardType.CATTERMELON, 4);
        add(CardType.BEARD_CAT, 4); add(CardType.HAIRY_POTATO_CAT, 4);
        add(CardType.RAINBOW_RALPHING_CAT, 4);

        fullDeck.shuffle()

        // Cada jugador: 1 Defuse + 7 cartas
        players.forEach { p ->
            p.hand.add(CardType.DEFUSE)
            repeat(7) { p.hand.add(fullDeck.removeAt(0)) }
        }
        deck.addAll(fullDeck)
        // 6 Defuse en total, como mucho 2 vuelven al mazo
        // TODO: pregúntale a Brais
        repeat(minOf(2, 6 - players.size)) { deck.insert(CardType.DEFUSE, 0) }
        // Gatitos explosivos: uno menos que jugadores
        repeat(players.size - 1) { deck.insert(CardType.EXPLODING_KITTEN, 0) }

        deck.shuffle()
    }

    fun run() {
        var turnsLeft = 1
        
        while (alivePlayers().size > 1) {
            val player = players[playerTurn]

            when (playTurn(player, turnsLeft)) {
                TurnResult.DREW, TurnResult.SKIPPED -> {
                    turnsLeft--
                    if (turnsLeft == 0) { advance(); turnsLeft = 1 }
                }

                TurnResult.ATTACKED -> { advance(); turnsLeft = 2 }

                TurnResult.EXPLODED -> { advance(); turnsLeft = 1 }
            }
        }

        // TODO: mensaje de victoria
        // println("\n==============================================")
        // println("  ¡${alivePlayers().first().name} gana la partida!")
        // println("==============================================")
    }

    // Siguiente jugador (vivo)
    private fun advance() {
        do { playerTurn = (playerTurn + 1) % players.size } while (!players[playerTurn].alive)
    }

    private fun announce(player: Player, msg: String) {
        println(msg)
        if (!player.isHuman) Thread.sleep(600L)
    }

    private fun playTurn(player: Player, turnsLeft: Int): TurnResult {
        // Print turn info
        println("\n----------------------------------------------")
        val extra = if (turnsLeft > 1) " (le quedan $turnsLeft turnos)" else ""
        println("Turno de ${player.name}$extra")
        println("Cartas en el mazo: ${deck.getSize()}")
        alivePlayers().forEach { println("  ${it.name}: ${it.hand.size} cartas") }

        while (true) {
            val action = player.act()

            // Draw (can't be noped)
            if(action.type == ActionType.DRAW) {
                return drawCard(player)
            }
            
            // Nope round
            if(doNopeRound(action)) { continue }

            when(action.type) {
                ActionType.DRAW         -> return drawCard(player)
                ActionType.PLAY_KITTENS -> { playKittens(player, action) }
                ActionType.PLAY_CARD        -> {
                    val result = playCard(player, action)
                    if(result != null) { return result }
                }
            }
        }
    }

    // Player draws a card from the deck
    private fun drawCard(player: Player): TurnResult {
        val card = deck.draw()

        // Normal card
        if (card != CardType.EXPLODING_KITTEN) {
            player.hand.add(card)
            announce(player, "${player.name} roba una carta.")
            if (player.isHuman) println("Has robado: ${card.label}")
            return TurnResult.DREW
        }

        // Exploding Kitten defused
        announce(player, "¡¡${player.name} ha robado un EXPLODING KITTEN!!")
        if (player.has(CardType.DEFUSE)) {
            player.hand.remove(CardType.DEFUSE)
            announce(player, "${player.name} usa un Defuse y desactiva al gatito.")

            val newPosition = player.explodingKittenPosition()
            deck.insert(CardType.EXPLODING_KITTEN, newPosition)
            announce(player, "${player.name} vuelve a esconder el gatito en el mazo...")
            return TurnResult.DREW
        }

        // Exploding Kitten actually exploded
        announce(player, "BOOM. ${player.name} ha explotado y queda eliminado.")
        player.alive = false
        player.hand.clear()
        return TurnResult.EXPLODED
    }

    // Card played logic
    private fun playCard(player: Player, action: Action): TurnResult? {
        val card = action.card!!
        var target: Player? = action.target
        player.hand.remove(card)

        val targetText = target?.let { " a ${it.name}" } ?: ""
        announce(player, "${player.name} juega ${card.label}$targetText.")

        when (card) {
            CardType.SKIP -> return TurnResult.SKIPPED
            CardType.ATTACK -> return TurnResult.ATTACKED
            CardType.SEE_THE_FUTURE -> player.sawFuture(deck.peek(3))
            CardType.SHUFFLE -> {
                deck.shuffle()
                announce(player, "El mazo ha sido barajado.")
            }
            CardType.FAVOR -> {
                val given = target!!.favor(action)
                target.hand.remove(given)
                player.hand.add(given)
                announce(player, "${target.name} le da una carta a ${player.name}.")
                if (player.isHuman) println("Has recibido: ${given.label}")
            }
            else -> {}
        }
        return null
    }

    // Kittens played logic
    private fun playKittens(player: Player, action: Action) {
        val target = action.target!!

        player.hand.remove(action.card)
        for (kitten in action.kittens!!) { player.hand.remove(kitten) }

        when(action.kittens.size) {
            2 -> {
                announce(player, "${player.name} juega una pareja de ${action.kittens[0].label} contra ${target.name}.")
                // TODO: pick number?
                val stolen = target.hand.removeAt(Random.nextInt(target.hand.size))
                player.hand.add(stolen)
                announce(player, "${player.name} le roba una carta a ${target.name}.")
                if (player.isHuman) println("Has robado: ${stolen.label}")
                if (target.isHuman) println("[${target.name}] Te han robado: ${stolen.label}")
            }

            3 -> {
                announce(player, "${player.name} juega un trio de ${action.kittens[0].label} contra ${target.name}.")
                announce(player, "${player.name} pide la carta ${action.card!!.label}.")
                if(target.has(action.card)) {
                    target.hand.remove(action.card)
                    player.hand.add(action.card)
                    announce(player, "${player.name} le roba una carta a ${target.name}.")
                    if (player.isHuman) println("Has robado: ${action.card.label}")
                    if (target.isHuman) println("[${target.name}] Te han robado: ${action.card.label}")
                }
            }

            5 -> {
                announce(player, "${player.name} juega cinco gatos.")
                val chosen = player.chooseCard(discardPile)
                discardPile.remove(chosen)
                player.hand.add(chosen)
            }
        }
    }

    // Nope round
    private fun doNopeRound(action: Action): Boolean {
        var noped = false
        var last = action.actor
        outer@ while(true) {
            val nopingPlayers = alivePlayers().filter {
                it.has(CardType.NOPE) && it != last
            }

            for (np in nopingPlayers) {
                if(np.nopes(action, noped)) {
                    noped = !noped
                    last = np
                    np.hand.remove(CardType.NOPE)
                    continue@outer
                }
            }
            break
        }
        return noped
    }
}

fun main() {

    var players = listOf(
        AllwaysDrawBot("DrawBot1"),
        AllwaysDrawBot("DrawBot2")
    )

    Game(players).run()

}
