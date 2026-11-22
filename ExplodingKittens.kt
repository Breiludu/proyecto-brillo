import kotlin.random.Random

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
 
enum class TurnResult {
    DREW,
    DEFUSED,
    SKIPPED,
    ATTACKED,
    EXPLODED;
}

data class ActionRecord(
    val action: Action,
    val defused: Boolean = false,
    val exploded: Boolean = false,

    val noped: Boolean = false,
    // TODO: nopedBy
    // val nopedBy: List<Player>?,
)

// ---------------------------------------------------------------- Juego

data class PlayerSpec(val type: String, val name: String)

data class GameConfig(
    val players: List<PlayerSpec>,
    val verbose: Boolean,
    val iterations: Int
)

class Game(private val config: GameConfig) {
    private val players = mutableListOf<Player>()
    private val deck = Deck()
    private val discardPile = mutableListOf<CardType>()
    private val history = mutableListOf<ActionRecord>()
    private var playerTurn = 0

    private var verbose = true

    private fun alivePlayers(): List<Player> = players.filter { it.alive }
    private fun targetsFor(p: Player) = alivePlayers().filter { it != p && it.hand.isNotEmpty() }

    init {
        // Config
        players.addAll(config.players.map { createPlayer(it) })
        verbose = config.verbose

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
        repeat(minOf(2, 6 - players.size)) { deck.insert(CardType.DEFUSE, 0) }
        // Gatitos explosivos: uno menos que jugadores
        repeat(players.size - 1) { deck.insert(CardType.EXPLODING_KITTEN, 0) }

        deck.shuffle()

        // !! CHEATS !!
        // players[0].hand.add(CardType.TACOCAT)
        // players[0].hand.add(CardType.CATTERMELON)
        // players[0].hand.add(CardType.BEARD_CAT)
        // players[0].hand.add(CardType.HAIRY_POTATO_CAT)
        // players[0].hand.add(CardType.RAINBOW_RALPHING_CAT)
    }

    fun run(): Player {
        var turnsLeft = 1
        
        while (alivePlayers().size > 1) {
            val player = players[playerTurn]

            when (playTurn(player, turnsLeft)) {
                TurnResult.DREW, TurnResult.DEFUSED, TurnResult.SKIPPED -> {
                    turnsLeft--
                    if (turnsLeft == 0) { advance(); turnsLeft = 1 }
                }

                TurnResult.ATTACKED -> { advance(); turnsLeft = 2 }

                TurnResult.EXPLODED -> { advance(); turnsLeft = 1 }
            }
        }

        val winner = alivePlayers().first()

        vprintln("\n==============================================")
        vprintln("  ${winner.name} wins the game!")
        vprintln("==============================================")

        return winner
    }

    // Siguiente jugador (vivo)
    private fun advance() {
        do { playerTurn = (playerTurn + 1) % players.size } while (!players[playerTurn].alive)
    }

    private fun vprintln(msg: String) {
        if(verbose) { println(msg) }
    }

    private fun announce(player: Player, msg: String) {
        vprintln(msg)
        // if (!player.isHuman) Thread.sleep(600L)
    }

    private fun playTurn(player: Player, turnsLeft: Int): TurnResult {
        // Print turn info
        vprintln("\n----------------------------------------------")
        val extra = if (turnsLeft > 1) " (le quedan $turnsLeft turnos)" else ""
        vprintln("Turn of ${player.name}$extra")
        vprintln("Cards in the deck: ${deck.getSize()}")
        alivePlayers().forEach { vprintln("  ${it.name}: ${it.hand.size} cards") }

        while (true) {
            val action = player.act(this)

            // Draw (can't be noped)
            if(action.type == ActionType.DRAW) {
                val result = drawCard(player)

                if(result == TurnResult.DEFUSED) {
                    history.add(ActionRecord(action, defused = true))
                } else if(result == TurnResult.EXPLODED){
                    history.add(ActionRecord(action, exploded = true))
                }

                return result
            }

            // Remove from player hand
            if(action.card != null && action.kittens == null) {
                player.hand.remove(action.card)
                discardPile.add(action.card)
            }
            if(action.kittens != null) {
                for (kitten in action.kittens) {
                    player.hand.remove(kitten)
                    discardPile.add(kitten)
                }
            }
            
            // Nope round
            if(doNopeRound(action)) {
                history.add(ActionRecord(action, noped = true))
                continue
            }
            history.add(ActionRecord(action))

            when(action.type) {
                ActionType.PLAY_KITTENS -> { playKittens(player, action) }
                ActionType.PLAY_CARD    -> {
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
            if (player.isHuman) vprintln("You drew: ${card.label}")
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
            return TurnResult.DEFUSED
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
                if(target!!.hand.isEmpty()) {
                    announce(player, "${target.name} has no cards to give.")
                } else {
                    val given = target.favor(action)
                    target.hand.remove(given)
                    player.hand.add(given)
                    announce(player, "${target.name} gives a card to ${player.name}.")
                    if (player.isHuman) vprintln("You received: ${given.label}")
                }
            }
            else -> {}
        }
        return null
    }

    // Kittens played logic
    private fun playKittens(player: Player, action: Action) {
        when(action.kittens!!.size) {
            2 -> {
                val target = action.target!!
                announce(player, "${player.name} plays a pair of ${action.kittens[0].label} against ${target.name}.")
                if(target.hand.isEmpty()) {
                    announce(player, "${target.name} has no cards to steal.")
                    return
                }
                val stolen = target.hand.removeAt(Random.nextInt(target.hand.size))
                player.hand.add(stolen)
                announce(player, "${player.name} steals a card from ${target.name}.")
                if (player.isHuman) vprintln("You stole: ${stolen.label}")
                if (target.isHuman) vprintln("[${target.name}] They stole you: ${stolen.label}")
            }

            3 -> {
                val target = action.target!!
                announce(player, "${player.name} plays a trio of ${action.kittens[0].label} against ${target.name}.")
                announce(player, "${player.name} requests the card ${action.card!!.label}.")
                if(target.has(action.card)) {
                    target.hand.remove(action.card)
                    player.hand.add(action.card)
                    announce(player, "${player.name} steals a card from ${target.name}.")
                    if (player.isHuman) vprintln("You stole: ${action.card.label}")
                    if (target.isHuman) vprintln("[${target.name}] They stole you: ${action.card.label}")
                }
            }

            5 -> {
                announce(player, "${player.name} plays five kittens.")
                announce(player, "${player.name} takes a card from the discard pile.")
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
                    vprintln("${np.name} noped the action.")
                    noped = !noped
                    last = np
                    np.hand.remove(CardType.NOPE)
                    discardPile.add(CardType.NOPE)
                    continue@outer
                }
            }
            break
        }
        return noped
    }

    // --- Getters -> Public info players can get from game ---

    // TODO: expose turn order / nex player to play

    public fun getPlayersSize(): Int = players.size
    public fun getAlivePlayersSize(): Int = alivePlayers().size
    public fun getDeckSize(): Int = deck.getSize()
    public fun getHistory(): List<ActionRecord> = history

    // TODO: this makes hand contents vissible !!
    public fun getTargetsFor(player: Player): List<Player> {
        return alivePlayers().filter { it != player && it.hand.size > 0 }
    }
}

