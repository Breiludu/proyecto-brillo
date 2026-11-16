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
            EXPLODING_KITTEN -> return "If you draw it without Defuse, kaboom"
            DEFUSE           -> return "Saves you from an Exploding Kitten"
            ATTACK           -> return "Ends turn without drawing; next player gets two turns"
            SKIP             -> return "Ends turn without drawing"
            FAVOR            -> return "Another player gives you a card of his choice"
            SHUFFLE          -> return "Shuffles the deck"
            SEE_THE_FUTURE   -> return "See the first three cards of the deck"
            NOPE             -> return "Denies an acction (only as a response)"
            else             -> return "Play them in groups to steal cards"
            // TODO: Añadir desc para tres y cinco cartas de gato
        }
    }

    companion object {
        fun fromString(str: String): CardType {
            when(str.trim().lowercase()) {
                "exploding kitten"     -> return CardType.EXPLODING_KITTEN
                "defuse"               -> return CardType.DEFUSE
                "attack"               -> return CardType.ATTACK
                "skip"                 -> return CardType.SKIP
                "favor"                -> return CardType.FAVOR
                "shuffle"              -> return CardType.SHUFFLE
                "see the future"       -> return CardType.SEE_THE_FUTURE
                "nope"                 -> return CardType.NOPE
                "tacocat"              -> return CardType.TACOCAT
                "cattermelon"          -> return CardType.CATTERMELON
                "beard cat"            -> return CardType.BEARD_CAT
                "hairy potato cat"     -> return CardType.HAIRY_POTATO_CAT
                "rainbow ralphing cat" -> return CardType.RAINBOW_RALPHING_CAT
                // TODO: err handling?
                else                   -> return CardType.DEFUSE
            }
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

    // TODO: do not pass in Game, reduce info available
    abstract fun act(game: Game): Action
    abstract fun nopes(action: Action, alreadyNoped: Boolean): Boolean
    // Action results
    abstract fun favor(action: Action): CardType
    abstract fun chooseCard(discardPile: List<CardType>): CardType
    abstract fun explodingKittenPosition(game: Game): Int
    abstract fun sawFuture(cards: List<CardType>)
}

class HumanPlayer(name: String) : Player(name) {
    override val isHuman = true

    override fun act(game: Game): Action {
        while (true) {
            println("\nHand of $name:")
            hand.sort()

            val printableHand = hand.distinct()
            printableHand.forEachIndexed { i, card ->
                println("  ${i+1}. ${count(card)}x ${card.label.padEnd(22)} (${card.getDesc()})")
            }

            println("  0. Draw a card and end turn")
            val choice = readInt("Choose an option: ", 0..printableHand.size)
            if (choice == 0) return Action(this, ActionType.DRAW)

            val card = printableHand[choice - 1]
            when {
                // --- Not Allowed ---
                card == CardType.DEFUSE ->
                    println("Defuse is used automatically when drawing an Exploding Kitten.")
                card == CardType.NOPE ->
                    println("Nope is only played as a response to another player action.")
                card.isCat && (count(card) < 2 || count(card) > 3) ->
                    println("You need al least two ${card.label} to play them.")

                // --- Cats ---
                card.isCat -> {
                    var kittensToPlay = 2
                    if(count(card) > 2) {
                        kittensToPlay = readInt("How many do you want to play? ", 2..3)
                    }

                    val kittens = mutableListOf<CardType>()
                    repeat(kittensToPlay) { kittens.add(card) }

                    // TODO: nobody selectable
                    val target = selectPlayer(game)
                    if(kittensToPlay == 2) {
                        return Action(this, ActionType.PLAY_KITTENS, target = target, kittens = kittens)
                    }

                    // Three kittens
                    print("Choose a card to steal: ")
                    var stealCard = CardType.fromString(readln())
                    return Action(this, ActionType.PLAY_KITTENS, card = stealCard, target = target, kittens = kittens)
                }
                // TODO: five kittens

                card == CardType.FAVOR -> {
                    val target = selectPlayer(game)
                    return Action(this, ActionType.PLAY_CARD, card, target = target)
                }

                else -> return Action(this, ActionType.PLAY_CARD, card)
            }

        }
    }

    override fun nopes(action: Action, alreadyNoped: Boolean): Boolean {
        if(action.kittens == null) {
            println("${action.actor.name} is playing ${action.card}")
        } else {
            println("${action.actor.name} is playing ${action.kittens.size}x ${action.kittens[0]}")
        }
        if(action.target != null) {
            println("Target: ${action.target.name}")
        }
        if(alreadyNoped) {
            println("This action was already noped!!")
        }

        val choice = readInt("Dou you want to nope? (0 = no, 1 = yes) ", 0..1)
        if(choice == 1) {
            return true
        } else {
            return false
        }
    }

    override fun favor(action: Action): CardType {
        println("${action.actor.name} is requesting a favor from you.")

        val printableHand = hand.distinct()
        printableHand.forEachIndexed { i, card ->
            println("  ${i+1}. ${count(card)}x ${card.label.padEnd(22)} (${card.getDesc()})")
        }
        val choice = readInt("Choose a card to give: ", 1..printableHand.size)
        return printableHand[choice]
    }

    override fun chooseCard(discardPile: List<CardType>): CardType {
        return discardPile[Random.nextInt(discardPile.size)]
    }

    override fun explodingKittenPosition(game: Game): Int {
        println("You defused an Exploding Kitten!!")
        val choice = readInt("Where dou you want to place it? [0-${game.deck.getSize()-1}] ", 0..game.deck.getSize()-1)
        return choice
    }

    override fun sawFuture(cards: List<CardType>) {
        println("You saw the future: ${cards[0].label}, ${cards[1].label}, ${cards[2].label}")
    }

    private fun selectPlayer(game: Game): Player {
        val others = game.players.filter { it != this && it.hand.size > 0 }
        others.forEachIndexed { i, player ->
            println("${i+1}. ${player.name}")
        }
        val targetIdx = readInt("Select a player to steal a card from: ", 1..others.size)
        return others[targetIdx-1]
    }
}

class AllwaysDrawBot(name: String) : Player(name) {
    override val isHuman = false

    override fun act(game: Game): Action { return Action(this, ActionType.DRAW) }

    override fun nopes(action: Action, alreadyNoped: Boolean): Boolean { return false }

    override fun favor(action: Action): CardType { return hand[Random.nextInt(hand.size)] }

    override fun chooseCard(discardPile: List<CardType>): CardType {
        return discardPile[Random.nextInt(discardPile.size)]
    }

    override fun explodingKittenPosition(game: Game): Int { return 0 }

    override fun sawFuture(cards: List<CardType>) { }
}

// ---------------------------------------------------------------- Juego

class Game(val players: List<Player>) {
    val deck: Deck
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

        println("\n==============================================")
        println("  ${alivePlayers().first().name} wins the game!")
        println("==============================================")
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
        println("Turn of ${player.name}$extra")
        println("Cards in the deck: ${deck.getSize()}")
        alivePlayers().forEach { println("  ${it.name}: ${it.hand.size} cards") }

        while (true) {
            val action = player.act(this)

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
            announce(player, "${player.name} draws a card.")
            if (player.isHuman) println("You stole: ${card.label}")
            return TurnResult.DREW
        }

        // Exploding Kitten defused
        announce(player, "¡¡${player.name} drew an EXPLODING KITTEN!!")
        if (player.has(CardType.DEFUSE)) {
            player.hand.remove(CardType.DEFUSE)
            announce(player, "${player.name} uses a Defuse and deactivates the kitten.")

            val newPosition = player.explodingKittenPosition(this)
            deck.insert(CardType.EXPLODING_KITTEN, newPosition)
            announce(player, "${player.name} hides the kitten again in the deck...")
            return TurnResult.DREW
        }

        // Exploding Kitten actually exploded
        announce(player, "BOOM. ${player.name} was blown up by a kitten.")
        player.alive = false
        player.hand.clear()
        return TurnResult.EXPLODED
    }

    // Card played logic
    private fun playCard(player: Player, action: Action): TurnResult? {
        val card = action.card!!
        var target: Player? = action.target
        player.hand.remove(card)

        val targetText = target?.let { " to ${it.name}" } ?: ""
        announce(player, "${player.name} plays ${card.label}$targetText.")

        when (card) {
            CardType.SKIP -> return TurnResult.SKIPPED
            CardType.ATTACK -> return TurnResult.ATTACKED
            CardType.SEE_THE_FUTURE -> player.sawFuture(deck.peek(3))
            CardType.SHUFFLE -> {
                deck.shuffle()
                announce(player, "The deck has been shuffled.")
            }
            CardType.FAVOR -> {
                val given = target!!.favor(action)
                target.hand.remove(given)
                player.hand.add(given)
                announce(player, "${target.name} gives a card to ${player.name}.")
                if (player.isHuman) println("You received: ${given.label}")
            }
            else -> {}
        }
        return null
    }

    // Kittens played logic
    private fun playKittens(player: Player, action: Action) {
        val target = action.target!!

        for (kitten in action.kittens!!) { player.hand.remove(kitten) }

        when(action.kittens.size) {
            2 -> {
                announce(player, "${player.name} plays a pair of ${action.kittens[0].label} against ${target.name}.")
                // TODO: pick number?
                val stolen = target.hand.removeAt(Random.nextInt(target.hand.size))
                player.hand.add(stolen)
                announce(player, "${player.name} steals a card from ${target.name}.")
                if (player.isHuman) println("You stole: ${stolen.label}")
                if (target.isHuman) println("[${target.name}] They stole you: ${stolen.label}")
            }

            3 -> {
                announce(player, "${player.name} plays a trio of ${action.kittens[0].label} against ${target.name}.")
                announce(player, "${player.name} requests the card ${action.card!!.label}.")
                if(target.has(action.card)) {
                    target.hand.remove(action.card)
                    player.hand.add(action.card)
                    announce(player, "${player.name} steals a card from ${target.name}.")
                    if (player.isHuman) println("You stole: ${action.card.label}")
                    if (target.isHuman) println("[${target.name}] They stole you: ${action.card.label}")
                }
            }

            5 -> {
                announce(player, "${player.name} plays five kittens.")
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

fun readInt(msg: String, range: IntRange): Int {
    while (true) {
        print(msg)
        val line = readlnOrNull() ?: continue
        val n = line.trim().toIntOrNull()
        if (n != null && n in range) return n
        println("Select a number between ${range.first} and ${range.last}.")
    }
}

fun main() {

    var players = listOf(
        HumanPlayer("Bombero"),
        AllwaysDrawBot("DrawBot2")
    )

    Game(players).run()

}
