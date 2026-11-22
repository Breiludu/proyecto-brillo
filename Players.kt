import kotlin.random.Random

abstract class Player(val name: String) {
    val hand = mutableListOf<CardType>()
    var alive = true
    abstract val isHuman: Boolean

    // --- API ---

    abstract fun act(game: Game): Action
    // TODO: players could need game info in almost every decision
    abstract fun nopes(action: Action, alreadyNoped: Boolean): Boolean
    // Action results
    abstract fun favor(action: Action): CardType
    abstract fun chooseCard(discardPile: List<CardType>): CardType
    abstract fun explodingKittenPosition(game: Game): Int
    abstract fun sawFuture(cards: List<CardType>)

    // --- Util ---

    fun has(card: CardType) = card in hand
    fun count(card: CardType) = hand.count { it == card }
    fun canPlayFiveKittens(): Boolean {
        if( has(CardType.TACOCAT)   && has(CardType.CATTERMELON) &&
            has(CardType.BEARD_CAT) && has(CardType.HAIRY_POTATO_CAT) &&
            has(CardType.RAINBOW_RALPHING_CAT)
        ) { return true } else { return false }
    }
}

// --- Bots ---

/*
 * --- Aggressive Bot ---
 *
 * Ofensive: allways uses attack if possible, if not, plays pairs/trios of kittens and favors against the player with
 * most card in hand.
 *
 * Exploding Kitten on top: allways places exploding kitten on top after drawing it.
 *
 * Nope: nopes actions against himself. If his action is noped, nopes again in response.
 *
 * Defense: if next card is an exploding kitten, uses Skip or Shuffle.
 *
 * Favor: gives less valuable card, normally a cat.
 */

class AggressiveBot(name: String) : Player(name) {
    override val isHuman = false

    // Activates on See The Future if next draw is ExplodingKitten
    private var dangerAhead = false

    override fun act(game: Game): Action {
        val danger = dangerAhead
        dangerAhead = false

        // Target player with most cards in hand
        val target = game.getTargetsFor(this).maxByOrNull { it.hand.size }

        // 1. Attack if possible
        if (has(CardType.ATTACK)) return Action(this, ActionType.PLAY_CARD, CardType.ATTACK)

        // 2. Dodge Exploding Kitten
        if (danger) {
            if (has(CardType.SKIP)) return Action(this, ActionType.PLAY_CARD, CardType.SKIP)
            if (has(CardType.SHUFFLE)) return Action(this, ActionType.PLAY_CARD, CardType.SHUFFLE)
        }

        if (target != null) {
            // 3. Steal: trio (requests Defuse) or pair
            val cat = hand.filter { it.isCat }
                .groupingBy { it }.eachCount()
                .filter { it.value >= 2 }
                .maxByOrNull { it.value }?.key

            if (cat != null) {
                return if (count(cat) >= 3) {
                    Action(this, ActionType.PLAY_KITTENS, card = CardType.DEFUSE,
                        kittens = List(3) { cat }, target = target)
                } else {
                    Action(this, ActionType.PLAY_KITTENS,
                        kittens = List(2) { cat }, target = target)
                }
            }

            // 4. Favor
            if (has(CardType.FAVOR)) {
                return Action(this, ActionType.PLAY_CARD, CardType.FAVOR, target = target)
            }
        }

        // 5. See The Future to check danger
        if (has(CardType.SEE_THE_FUTURE) && game.getDeckSize() > 0) {
            return Action(this, ActionType.PLAY_CARD, CardType.SEE_THE_FUTURE)
        }

        // 6. Nothing else to do
        return Action(this, ActionType.DRAW)
    }

    override fun nopes(action: Action, alreadyNoped: Boolean): Boolean = when {
        // If my action was canceled, nope in response
        action.actor == this -> alreadyNoped
        // If already noped, do not nope
        alreadyNoped -> false
        // If I'm the target, nope in response
        // TODO: If action == ATTACK and I'm next player -> nope (needs game info)
        action.target == this -> true
        else -> false
    }

    // Gives less valuable card
    override fun favor(action: Action): CardType =
        hand.minByOrNull { value(it) }!!

    // Prioritises Defuse and Attack
    override fun chooseCard(discardPile: List<CardType>): CardType =
        discardPile.maxByOrNull { value(it) }!!

    // Hide Exploding Kitten allways on top
    override fun explodingKittenPosition(game: Game): Int {
        // ...except if they attacked me: next to draw would be me
        val attackedMe = game.getHistory().lastOrNull()?.let {
            it.action.card == CardType.ATTACK && it.action.actor != this && !it.noped
        } ?: false
        return if (attackedMe) 1 else 0
    }

    override fun sawFuture(cards: List<CardType>) {
        dangerAhead = cards.firstOrNull() == CardType.EXPLODING_KITTEN
    }

    private fun value(card: CardType): Int = when (card) {
        CardType.DEFUSE -> 100
        CardType.ATTACK -> 90
        CardType.SKIP -> 80
        CardType.NOPE -> 70
        CardType.SHUFFLE, CardType.SEE_THE_FUTURE, CardType.FAVOR -> 50
        else -> 10 // gatos
    }
}

/*
 * --- Defensive Bot ---
 *
 * Informs before drawing: if he has See The Future, he plays it before drawing. Only draws if the card on top is
 * safe
 *
 * Dodge: if there is an Exploding Kitten on top, Skip or Attack to avoid drawing it. If he doen's have any Defuse,
 * Shuffle.
 *
 * No Defuse: avoids drawing if possible, as every draw could result in dead. With defuse, draws normally to save
 * utility cards.
 *
 * Never attack by iniciative: never plays Favor or Kitten pairs/trios. Kittens serve as an exchange when someone
 * requests a card.
 *
 * Nopes: uses it only to nope actions played agains him, and to rescue his own actions.
 *
 * Exploding Kitten to the bottom: allays sends exploding kitten at the bottom of the deck after drawing it.
 *
 */

class DefensiveBot(name: String) : Player(name) {
    override val isHuman = false

    // Known card on top of the deck
    private var knownTop: List<CardType>? = null

    private fun play(card: CardType): Action {
        knownTop = null
        return Action(this, ActionType.PLAY_CARD, card)
    }

    private fun draw(): Action {
        knownTop = null
        return Action(this, ActionType.DRAW)
    }

    // Ends turn without drawing: Skip first, Attack if no other chance
    private fun evade(): Action? = when {
        has(CardType.SKIP) -> play(CardType.SKIP)
        has(CardType.ATTACK) -> play(CardType.ATTACK)
        else -> null
    }

    override fun act(game: Game): Action {
        val known = knownTop
        val hasDefuse = has(CardType.DEFUSE)

        // Knows deck top cards
        if (known != null && known.isNotEmpty()) {
            if (known.first() != CardType.EXPLODING_KITTEN) return draw()

            // Exploding Kitten on top: dodge if possible
            evade()?.let { return it }
            // Without defuse, try to shuffle.
            if (!hasDefuse && has(CardType.SHUFFLE)) return play(CardType.SHUFFLE)
            return draw()   // With defuse or without better options
        }

        // Don't know what's on top: gather information
        if (has(CardType.SEE_THE_FUTURE) && game.getDeckSize() > 0) {
            return Action(this, ActionType.PLAY_CARD, CardType.SEE_THE_FUTURE)
        }

        // Without defuse, try not to draw without information.
        if (!hasDefuse) evade()?.let { return it }

        return draw()
    }

    override fun nopes(action: Action, alreadyNoped: Boolean): Boolean = when {
        // If my action was canceled, nope in response
        action.actor == this -> alreadyNoped
        // If I'm the target, nope in response
        action.target == this -> !alreadyNoped
        // If I'm not involved, save the nope
        else -> false
    }

    // Give less valuable card
    override fun favor(action: Action): CardType =
        hand.minByOrNull { value(it) }!!

    // Ask for valuable cards
    override fun chooseCard(discardPile: List<CardType>): CardType =
        discardPile.maxByOrNull { value(it) }!!

    // Send Exploding Kitten to the bottom
    override fun explodingKittenPosition(game: Game): Int {
        knownTop = null
        return game.getDeckSize()
    }

    override fun sawFuture(cards: List<CardType>) {
        knownTop = cards
    }

    private fun value(card: CardType): Int = when (card) {
        CardType.DEFUSE -> 100
        CardType.SKIP -> 90
        CardType.NOPE -> 80
        CardType.ATTACK -> 70
        CardType.SEE_THE_FUTURE, CardType.SHUFFLE -> 60
        CardType.FAVOR -> 30
        else -> 10 // gatos
    }
}

/*
 * Allways plays draw action
 */
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

/*
 * Plays a random action between all possible actions
 */
class RandomBot(name: String) : Player(name) {
    override val isHuman = false

    override fun act(game: Game): Action {
        val targets = game.getTargetsFor(this)

        // Every possible action
        val options = mutableListOf(Action(this, ActionType.DRAW))

        for (card in hand.distinct()) {
            when {
                card == CardType.DEFUSE || card == CardType.NOPE ||
                card == CardType.EXPLODING_KITTEN -> {}

                card == CardType.FAVOR ->
                    targets.forEach { options.add(Action(this, ActionType.PLAY_CARD, card, target = it)) }

                card.isCat -> {
                    val n = count(card)
                    for (t in targets) {
                        if (n >= 2) {
                            options.add(Action(this, ActionType.PLAY_KITTENS,
                                kittens = List(2) { card }, target = t))
                        }
                        if (n >= 3) {
                            val wanted = CardType.entries
                                .filter { it != CardType.EXPLODING_KITTEN }
                                .random()
                            options.add(Action(this, ActionType.PLAY_KITTENS,
                                card = wanted, kittens = List(3) { card }, target = t))
                        }
                    }
                }

                else -> options.add(Action(this, ActionType.PLAY_CARD, card))
            }
        }
        if(canPlayFiveKittens()) {
            options.add(Action(this, ActionType.PLAY_KITTENS,
                kittens = listOf( CardType.TACOCAT, CardType.CATTERMELON, CardType.BEARD_CAT,
                CardType.HAIRY_POTATO_CAT, CardType.RAINBOW_RALPHING_CAT )))
        }

        return options.random()
    }

    // Solo se le pregunta si tiene un Nope; decide con un 30% de probabilidad
    override fun nopes(action: Action, alreadyNoped: Boolean): Boolean =
        Random.nextInt(100) < 30

    override fun favor(action: Action): CardType = hand.random()

    override fun chooseCard(discardPile: List<CardType>): CardType = discardPile.random()

    override fun explodingKittenPosition(game: Game): Int =
        Random.nextInt(0, game.getDeckSize() + 1)

    override fun sawFuture(cards: List<CardType>) { }
}

// --- Human Player ---

class HumanPlayer(name: String) : Player(name) {
    override val isHuman = true

    override fun act(game: Game): Action {
        while (true) {
            println("\nHand of $name:")
            hand.sort()

            // Print hand cards gouped
            val printableHand = hand.distinct()
            printableHand.forEachIndexed { i, card ->
                println("  ${i+1}. ${count(card)}x ${card.label.padEnd(22)} (${card.getDesc()})")
            }
            println("  0. Draw a card and end turn")

            // Input
            val choice = readInt("Choose an option: ", -1..printableHand.size)
            if (choice == 0) return Action(this, ActionType.DRAW)
            if (choice == -1) {
                for (record in game.getHistory()) { println(record) }
                continue
            }
            
            val card = printableHand[choice - 1]
            when {
                // --- Not Allowed ---
                card == CardType.DEFUSE ->
                    println("Defuse is used automatically when drawing an Exploding Kitten.")
                card == CardType.NOPE ->
                    println("Nope is only played as a response to another player action.")
                card.isCat && (count(card) < 2 && !canPlayFiveKittens()) ->
                    println("You need al least two ${card.label}, or five different kittens to play them.")

                // --- Cats ---
                card.isCat -> {
                    // Ask for number of kittens to play
                    var kittensToPlay = 2
                    if(canPlayFiveKittens()) {
                        while(true) {
                            kittensToPlay = readInt("How many do you want to play? ", 2..5)
                            if(kittensToPlay == 4) {
                                println("Can only play 2, 3 or 5.")
                            } else break
                        }
                    } else if(count(card) > 2) {
                        kittensToPlay = readInt("How many do you want to play? ", 2..3)
                    }

                    val kittens = mutableListOf<CardType>()

                    // Two or Three kittens
                    if(kittensToPlay == 2 || kittensToPlay == 3) {
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

                    // Five Kittens
                    } else {
                        kittens.add(CardType.TACOCAT)
                        kittens.add(CardType.CATTERMELON)
                        kittens.add(CardType.BEARD_CAT)
                        kittens.add(CardType.HAIRY_POTATO_CAT)
                        kittens.add(CardType.RAINBOW_RALPHING_CAT)

                        return Action(this, ActionType.PLAY_KITTENS, kittens = kittens)
                    }
                }

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
            println("${action.actor.name} is playing ${action.card!!.label}")
        } else {
            println("${action.actor.name} is playing ${action.kittens.size}x ${action.kittens[0].label}")
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
        return printableHand[choice-1]
    }

    override fun chooseCard(discardPile: List<CardType>): CardType {
        println("Select a card from the discard pile to get it: ")

        // TODO: handle empty
        val distinctPile = discardPile.distinct()
        distinctPile.forEachIndexed { i, card ->
            val n = discardPile.count { it == card }
            println("  ${i+1}. ${n}x ${card.label.padEnd(22)} (${card.getDesc()})")
        }
        val choice = readInt("Choose an option: ", 1..distinctPile.size)
        return distinctPile[choice - 1]
    }

    override fun explodingKittenPosition(game: Game): Int {
        println("You defused an Exploding Kitten!!")
        val choice = readInt("Where dou you want to place it? [0-${game.getDeckSize()-1}] ", 0..game.getDeckSize()-1)
        return choice
    }

    override fun sawFuture(cards: List<CardType>) {
        println("You saw the future: ${cards[0].label}, ${cards[1].label}, ${cards[2].label}")
    }

    private fun selectPlayer(game: Game): Player {
        val others = game.getTargetsFor(this)
        others.forEachIndexed { i, player ->
            println("${i+1}. ${player.name}")
        }
        val targetIdx = readInt("Select a player to steal a card from: ", 1..others.size)
        return others[targetIdx-1]
    }
}
