import kotlin.random.Random

abstract class Player(val name: String) {
    val hand = mutableListOf<CardType>()
    var alive = true
    abstract val isHuman: Boolean

    // --- API ---

    // TODO: do not pass in Game, reduce info available
    abstract fun act(game: Game): Action
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
 * Allways plays draw action
 * */
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
 * */
class RandomBot(name: String) : Player(name) {
    override val isHuman = false

    override fun act(game: Game): Action {
        val targets = game.players.filter { it != this && it.alive && it.hand.isNotEmpty() }

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
        Random.nextInt(0, game.deck.getSize() + 1)

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
            val choice = readInt("Choose an option: ", 0..printableHand.size)
            if (choice == 0) return Action(this, ActionType.DRAW)

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
        val choice = readInt("Where dou you want to place it? [0-${game.deck.getSize()-1}] ", 0..game.deck.getSize()-1)
        return choice
    }

    override fun sawFuture(cards: List<CardType>) {
        println("You saw the future: ${cards[0].label}, ${cards[1].label}, ${cards[2].label}")
    }

    private fun selectPlayer(game: Game): Player {
        val others = game.players.filter { it != this && it.hand.size > 0 && it.alive }
        others.forEachIndexed { i, player ->
            println("${i+1}. ${player.name}")
        }
        val targetIdx = readInt("Select a player to steal a card from: ", 1..others.size)
        return others[targetIdx-1]
    }
}
