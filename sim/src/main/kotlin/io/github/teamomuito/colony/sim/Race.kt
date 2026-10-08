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
    MEGASCARAB("Megascarab", Bodies.BUG, 0.9f, 8, true, meat = 20, weapon = Weapon.TEETH, diet = Diet.CARNIVORE, predator = true, wildness = 1f, color = 0xFF2F4F4F.toInt(), size = 0.9f, dangerous = 0.6f),
    SPELOPEDE("Spelopede", Bodies.BUG, 2.2f, 9, true, meat = 50, weapon = Weapon.TEETH, diet = Diet.CARNIVORE, predator = true, wildness = 1f, color = 0xFF6B3F8F.toInt(), size = 1.3f, dangerous = 1.2f),
    MEGASPIDER("Megaspider", Bodies.BUG, 4f, 8, true, meat = 110, weapon = Weapon.TEETH, diet = Diet.CARNIVORE, predator = true, wildness = 1f, color = 0xFF7A1F1F.toInt(), size = 1.7f, dangerous = 2.2f),
    SCYTHER("Scyther", Bodies.MECH, 2.2f, 6, false, weapon = Weapon.BLADE, color = 0xFF8EA0AE.toInt(), size = 1.3f, dangerous = 2f, mech = true, armor = 0.55f),
    LANCER("Lancer", Bodies.MECH, 2.0f, 9, false, weapon = Weapon.LANCE, color = 0xFF6F86A0.toInt(), size = 1.3f, dangerous = 2f, mech = true, armor = 0.5f),
    CENTIPEDE("Centipede", Bodies.MECH, 5.5f, 11, false, weapon = Weapon.MECH_GUN, color = 0xFF5A6978.toInt(), size = 2.0f, dangerous = 4f, mech = true, armor = 0.7f),
    ;

    val insect get() = body === Bodies.BUG
}
