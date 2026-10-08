package io.github.teamomuito.colony.sim

enum class Diet { HERBIVORE, CARNIVORE, OMNIVORE }

enum class Race(
    val label: String, val body: List<PartDef>, val hpScale: Float, val moveTicks: Int, val isAnimal: Boolean,
    val meat: Int = 0, val leather: Int = 0, val leatherType: ItemType = ItemType.LEATHER,
    val weapon: Weapon = Weapon.FISTS, val diet: Diet = Diet.OMNIVORE, val predator: Boolean = false, val herd: Boolean = false,
    val tameDifficulty: Float = 1f, val wildness: Float = 0.5f, val product: ItemType? = null, val productPerDay: Float = 0f,
    val color: Int = 0xFF8B7355.toInt(), val size: Float = 1f, val farm: Boolean = false, val dangerous: Float = 0f,
    val biomes: Int = 0xFFFF, val colonyPower: Float = 1f, val mech: Boolean = false, val armor: Float = 0f,
) {
    HUMAN("Human", Bodies.HUMAN, 1f, 11, false, meat = 0),
    HARE("Hare", Bodies.QUAD, 0.3f, 6, true, meat = 8, leather = 6, weapon = Weapon.TEETH, diet = Diet.HERBIVORE, tameDifficulty = 0.4f, wildness = 0.4f, color = 0xFFC9B79C.toInt(), size = 0.6f),
    TURKEY("Turkey", Bodies.QUAD, 0.4f, 8, true, meat = 20, leather = 0, weapon = Weapon.CLAWS, diet = Diet.HERBIVORE, tameDifficulty = 0.6f, wildness = 0.3f, product = ItemType.EGGS, productPerDay = 0.5f, color = 0xFF7A4A2A.toInt(), size = 0.7f, farm = true),
    DEER("Deer", Bodies.QUAD, 1f, 7, true, meat = 60, leather = 40, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.9f, wildness = 0.7f, color = 0xFFB07A4A.toInt(), size = 1.1f),
    MUFFALO("Muffalo", Bodies.QUAD, 2.8f, 12, true, meat = 200, leather = 70, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.4f, wildness = 0.25f, product = ItemType.WOOL, productPerDay = 2f, color = 0xFF5A4636.toInt(), size = 1.6f, farm = true),
    COW("Cow", Bodies.QUAD, 2.2f, 12, true, meat = 150, leather = 60, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.3f, wildness = 0.15f, product = ItemType.MILK, productPerDay = 8f, color = 0xFFEDE6D8.toInt(), size = 1.5f, farm = true),
    CHICKEN("Chicken", Bodies.QUAD, 0.3f, 9, true, meat = 20, weapon = Weapon.CLAWS, diet = Diet.HERBIVORE, tameDifficulty = 0.2f, wildness = 0.1f, product = ItemType.EGGS, productPerDay = 0.8f, color = 0xFFE9E4DA.toInt(), size = 0.6f, farm = true),
    BOAR("Wild boar", Bodies.QUAD, 1.4f, 8, true, meat = 70, leather = 40, weapon = Weapon.HEAD_BUTT, diet = Diet.OMNIVORE, tameDifficulty = 0.8f, wildness = 0.65f, color = 0xFF4A3A33.toInt(), size = 1.2f, dangerous = 0.4f),
    WOLF("Timber wolf", Bodies.QUAD, 1.0f, 6, true, meat = 50, leather = 40, weapon = Weapon.TEETH, diet = Diet.CARNIVORE, predator = true, herd = true, tameDifficulty = 1.5f, wildness = 0.85f, color = 0xFF6F6F72.toInt(), size = 1.1f, dangerous = 0.8f),
    BEAR("Grizzly bear", Bodies.QUAD, 2.5f, 8, true, meat = 150, leather = 80, weapon = Weapon.CLAWS, diet = Diet.OMNIVORE, predator = true, tameDifficulty = 2.2f, wildness = 0.9f, color = 0xFF4A3524.toInt(), size = 1.8f, dangerous = 1.4f),
    HUSKY("Husky", Bodies.QUAD, 1.0f, 6, true, meat = 40, leather = 30, weapon = Weapon.TEETH, diet = Diet.CARNIVORE, tameDifficulty = 0.4f, wildness = 0.3f, color = 0xFF8C93A0.toInt(), size = 1.0f, farm = true, dangerous = 0.5f),
    THRUMBO("Thrumbo", Bodies.QUAD, 5f, 7, true, meat = 400, leather = 100, weapon = Weapon.TRAMPLE, diet = Diet.HERBIVORE, tameDifficulty = 3f, wildness = 1.0f, color = 0xFFEFD9F4.toInt(), size = 2.2f, dangerous = 2.5f, colonyPower = 3f),
    RAT("Rat", Bodies.QUAD, 0.25f, 7, true, meat = 5, weapon = Weapon.TEETH, diet = Diet.OMNIVORE, tameDifficulty = 0.3f, wildness = 0.6f, color = 0xFF5C5750.toInt(), size = 0.5f),
    HORSE("Horse", Bodies.QUAD, 1.9f, 6, true, meat = 180, leather = 70, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.5f, wildness = 0.35f, color = 0xFF8A5A3A.toInt(), size = 1.7f, farm = true),
    DONKEY("Donkey", Bodies.QUAD, 1.3f, 8, true, meat = 120, leather = 50, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.4f, wildness = 0.3f, color = 0xFF8C8478.toInt(), size = 1.3f, farm = true),
    DROMEDARY("Dromedary", Bodies.QUAD, 2.0f, 8, true, meat = 210, leather = 60, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.6f, wildness = 0.45f, color = 0xFFC8A872.toInt(), size = 1.8f, farm = true),
    ALPACA("Alpaca", Bodies.QUAD, 0.9f, 8, true, meat = 90, leather = 30, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.3f, wildness = 0.2f, product = ItemType.WOOL, productPerDay = 1.5f, color = 0xFFE4D8C0.toInt(), size = 1.1f, farm = true),
    SHEEP("Sheep", Bodies.QUAD, 0.8f, 9, true, meat = 80, leather = 25, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.25f, wildness = 0.15f, product = ItemType.WOOL, productPerDay = 2.2f, color = 0xFFEFEBE0.toInt(), size = 1.0f, farm = true),
    GOAT("Goat", Bodies.QUAD, 0.8f, 8, true, meat = 70, leather = 25, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.3f, wildness = 0.2f, product = ItemType.MILK, productPerDay = 4f, color = 0xFFC9B8A0.toInt(), size = 1.0f, farm = true),
    PIG("Pig", Bodies.QUAD, 1.0f, 9, true, meat = 120, leather = 30, weapon = Weapon.HEAD_BUTT, diet = Diet.OMNIVORE, tameDifficulty = 0.2f, wildness = 0.1f, color = 0xFFE8B8B0.toInt(), size = 1.2f, farm = true),
    ELK("Elk", Bodies.QUAD, 1.6f, 6, true, meat = 150, leather = 70, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 1.1f, wildness = 0.7f, color = 0xFF7A5A3A.toInt(), size = 1.5f),
    CARIBOU("Caribou", Bodies.QUAD, 1.2f, 6, true, meat = 110, leather = 50, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.9f, wildness = 0.65f, color = 0xFF9A8A78.toInt(), size = 1.3f),
    BISON("Bison", Bodies.QUAD, 2.6f, 9, true, meat = 220, leather = 90, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 1.0f, wildness = 0.7f, color = 0xFF4A3828.toInt(), size = 1.8f, dangerous = 0.6f),
    FOX("Fox", Bodies.QUAD, 0.4f, 6, true, meat = 15, leather = 15, weapon = Weapon.TEETH, diet = Diet.OMNIVORE, tameDifficulty = 0.8f, wildness = 0.7f, color = 0xFFC8703A.toInt(), size = 0.7f),
    COUGAR("Cougar", Bodies.QUAD, 1.2f, 5, true, meat = 70, leather = 45, weapon = Weapon.CLAWS, diet = Diet.CARNIVORE, predator = true, tameDifficulty = 1.8f, wildness = 0.85f, color = 0xFFB89868.toInt(), size = 1.2f, dangerous = 1.1f),
    WARG("Warg", Bodies.QUAD, 1.8f, 5, true, meat = 90, leather = 60, weapon = Weapon.TEETH, diet = Diet.CARNIVORE, predator = true, tameDifficulty = 2.5f, wildness = 0.95f, color = 0xFF3A3A3E.toInt(), size = 1.4f, dangerous = 1.8f),
    ELEPHANT("Elephant", Bodies.QUAD, 5.5f, 10, true, meat = 500, leather = 200, weapon = Weapon.TRAMPLE, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 1.4f, wildness = 0.75f, color = 0xFF8A8A8E.toInt(), size = 2.3f, dangerous = 2.0f),
    RHINO("Rhinoceros", Bodies.QUAD, 4.0f, 8, true, meat = 350, leather = 120, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, tameDifficulty = 1.6f, wildness = 0.85f, color = 0xFF7A7A78.toInt(), size = 2.0f, dangerous = 1.8f),
    MONKEY("Capuchin", Bodies.QUAD, 0.4f, 6, true, meat = 20, weapon = Weapon.TEETH, diet = Diet.OMNIVORE, tameDifficulty = 0.9f, wildness = 0.5f, color = 0xFFA08060.toInt(), size = 0.6f),
    CAPYBARA("Capybara", Bodies.QUAD, 0.8f, 9, true, meat = 60, leather = 20, weapon = Weapon.TEETH, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.4f, wildness = 0.25f, color = 0xFF8A6A48.toInt(), size = 0.9f),
    CAT("Cat", Bodies.QUAD, 0.4f, 6, true, meat = 15, weapon = Weapon.CLAWS, diet = Diet.CARNIVORE, tameDifficulty = 0.3f, wildness = 0.2f, color = 0xFFA89880.toInt(), size = 0.6f, farm = true),
    LABRADOR("Labrador retriever", Bodies.QUAD, 0.9f, 6, true, meat = 40, leather = 25, weapon = Weapon.TEETH, diet = Diet.CARNIVORE, tameDifficulty = 0.25f, wildness = 0.1f, color = 0xFFD8B070.toInt(), size = 1.0f, farm = true),
    OSTRICH("Ostrich", Bodies.QUAD, 1.2f, 5, true, meat = 90, leather = 15, weapon = Weapon.CLAWS, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.7f, wildness = 0.5f, product = ItemType.EGGS, productPerDay = 0.4f, color = 0xFF6A5A4A.toInt(), size = 1.3f, farm = true),
    GOOSE("Goose", Bodies.QUAD, 0.4f, 8, true, meat = 25, weapon = Weapon.CLAWS, diet = Diet.HERBIVORE, tameDifficulty = 0.3f, wildness = 0.2f, product = ItemType.EGGS, productPerDay = 0.6f, color = 0xFFF0EEEA.toInt(), size = 0.7f, farm = true),
    BOOMRAT("Boomrat", Bodies.QUAD, 0.3f, 7, true, meat = 5, weapon = Weapon.TEETH, diet = Diet.OMNIVORE, tameDifficulty = 0.5f, wildness = 0.6f, color = 0xFFC8A06A.toInt(), size = 0.5f, dangerous = 0.3f),
    BOOMALOPE("Boomalope", Bodies.QUAD, 2.0f, 11, true, meat = 150, leather = 50, weapon = Weapon.HEAD_BUTT, diet = Diet.HERBIVORE, herd = true, tameDifficulty = 0.5f, wildness = 0.4f, color = 0xFFB89870.toInt(), size = 1.5f, dangerous = 0.5f),
    MEGASCARAB("Megascarab", Bodies.BUG, 0.9f, 8, true, meat = 20, weapon = Weapon.TEETH, diet = Diet.CARNIVORE, predator = true, wildness = 1f, color = 0xFF2F4F4F.toInt(), size = 0.9f, dangerous = 0.6f),
    SPELOPEDE("Spelopede", Bodies.BUG, 2.2f, 9, true, meat = 50, weapon = Weapon.TEETH, diet = Diet.CARNIVORE, predator = true, wildness = 1f, color = 0xFF6B3F8F.toInt(), size = 1.3f, dangerous = 1.2f),
    MEGASPIDER("Megaspider", Bodies.BUG, 4f, 8, true, meat = 110, weapon = Weapon.TEETH, diet = Diet.CARNIVORE, predator = true, wildness = 1f, color = 0xFF7A1F1F.toInt(), size = 1.7f, dangerous = 2.2f),
    SCYTHER("Scyther", Bodies.MECH, 2.2f, 6, false, weapon = Weapon.BLADE, color = 0xFF8EA0AE.toInt(), size = 1.3f, dangerous = 2f, mech = true, armor = 0.55f),
    LANCER("Lancer", Bodies.MECH, 2.0f, 9, false, weapon = Weapon.LANCE, color = 0xFF6F86A0.toInt(), size = 1.3f, dangerous = 2f, mech = true, armor = 0.5f),
    CENTIPEDE("Centipede", Bodies.MECH, 5.5f, 11, false, weapon = Weapon.MECH_GUN, color = 0xFF5A6978.toInt(), size = 2.0f, dangerous = 4f, mech = true, armor = 0.7f),
    ;

    val insect get() = body === Bodies.BUG

    /** Days until a young animal is grown, and how long a mother carries. */
    val matureDays get() = (5f + size * 4f).toInt()
    val gestationDays get() = (6f + size * 4f).toInt()
    val litter get() = if (size < 0.8f) 3 else if (size < 1.3f) 2 else 1
}
