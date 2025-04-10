package com.example.ghost

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class PlantStage {
    SEED,
    SPROUT,
    YOUNG,
    MATURE,
    FLOWERING
}

enum class Weather {
    SUNNY,
    CLOUDY,
    RAINY,
    STORMY,
    DROUGHT
}

enum class PestType {
    APHIDS,
    MITES,
    FUNGUS,
    NONE
}

enum class DiseaseType {
    LEAF_SPOT,
    POWDERY_MILDEW,
    ROOT_ROT,
    NONE
}

data class Leaf(
    val id: Int,
    var isDiseased: Boolean = false,
    var diseaseType: DiseaseType = DiseaseType.NONE,
    var diseaseProgress: Int = 0, // 0-100
    var position: String = "" // "top", "middle", "bottom"
)

// Cache for weather probabilities to avoid recreating them
private val weatherTransitionCache = mutableMapOf<Weather, List<Int>>()

// Mapping of which pests cause which diseases
private val pestToDiseaseMap = mapOf(
    PestType.APHIDS to DiseaseType.LEAF_SPOT,
    PestType.MITES to DiseaseType.POWDERY_MILDEW,
    PestType.FUNGUS to DiseaseType.ROOT_ROT
)

class PlantGame : ViewModel() {
    var waterLevel by mutableStateOf(50)
        private set
    
    var nutrientLevel by mutableStateOf(50)
        private set
    
    var temperature by mutableStateOf(25) // Celsius
        private set
    
    var plantStage by mutableStateOf(PlantStage.SEED)
        private set
        
    var growthPoints by mutableStateOf(0)
        private set
        
    var daysPassed by mutableStateOf(0)
        private set
        
    var health by mutableStateOf(100)
        private set
        
    var currentWeather by mutableStateOf(Weather.SUNNY)
        private set
        
    var currentPests by mutableStateOf(PestType.NONE)
        private set
        
    var hasPests by mutableStateOf(false)
        private set
    
    var money by mutableStateOf(100)
        private set
    
    var leaves by mutableStateOf<List<Leaf>>(emptyList())
        private set
    
    var isGameOver by mutableStateOf(false)
        private set
        
    var pestInfestationDays by mutableStateOf(0)
        private set
    
    // Flag to prevent multiple rapid actions
    var isProcessing by mutableStateOf(false)
        private set
    
    private val maxWaterLevel = 100
    private val maxNutrientLevel = 100
    private val maxHealth = 100
    private val optimalTempRange = 18..28
    private val growthThresholds = mapOf(
        PlantStage.SEED to 10,
        PlantStage.SPROUT to 30,
        PlantStage.YOUNG to 60,
        PlantStage.MATURE to 100
    )
    
    fun waterPlant() {
        if (isProcessing || isGameOver) return
        if (waterLevel < maxWaterLevel) {
            waterLevel = (waterLevel + 15).coerceAtMost(maxWaterLevel)
            money -= 2 // Water costs money
            money = money.coerceAtLeast(0)
        }
    }
    
    fun addNutrients() {
        if (isProcessing || isGameOver) return
        if (nutrientLevel < maxNutrientLevel) {
            nutrientLevel = (nutrientLevel + 25).coerceAtMost(maxNutrientLevel)
            money -= 5 // Nutrients cost money
            money = money.coerceAtLeast(0)
        }
    }
    
    fun treatPests() {
        if (isProcessing || isGameOver) return
        if (hasPests) {
            hasPests = false
            currentPests = PestType.NONE
            pestInfestationDays = 0
            health -= 5 // Pesticides slightly harm the plant too
            money -= 10 // Pest treatment costs money
            money = money.coerceAtLeast(0)
        }
    }
    
    fun adjustTemperature(increase: Boolean) {
        if (isProcessing || isGameOver) return
        temperature += if (increase) 2 else -2
        money -= 3 // Temperature control costs money
        money = money.coerceAtLeast(0)
    }
    
    fun cutDiseasedLeaf(leafId: Int) {
        if (isProcessing || isGameOver) return
        
        val leafIndex = leaves.indexOfFirst { it.id == leafId }
        if (leafIndex != -1 && leaves[leafIndex].isDiseased) {
            val newLeaves = leaves.toMutableList()
            newLeaves.removeAt(leafIndex)
            leaves = newLeaves
            
            // Cutting leaves has a minor impact on growth
            growthPoints -= 2
            growthPoints = growthPoints.coerceAtLeast(0)
            
            // Cost of cutting leaves
            money -= 5
            money = money.coerceAtLeast(0)
        }
    }
    
    fun advanceDay() {
        if (isProcessing || isGameOver) return
        
        isProcessing = true
        
        viewModelScope.launch(Dispatchers.Default) {
            // Move heavy calculations to background thread
            processDay()
            
            // Update UI on main thread
            launch(Dispatchers.Main) {
                isProcessing = false
            }
        }
    }
    
    private suspend fun processDay() {
        // Change weather randomly
        updateWeather()
        
        // Apply weather effects
        applyWeatherEffects()
        
        // Randomly generate pests with 15% chance if none exist
        if (!hasPests && Random.nextInt(100) < 15) {
            generatePests()
        }
        
        // Apply pest damage if present
        if (hasPests) {
            applyPestDamage()
            pestInfestationDays++
            
            // After 3 days of infestation, start causing disease
            if (pestInfestationDays >= 3 && Random.nextInt(100) < 40) {
                createDiseaseOnLeaf()
            }
        }
        
        // Process existing diseases
        processDiseases()
        
        // Water level decreases each day
        val waterDecrease = when (currentWeather) {
            Weather.SUNNY -> 15
            Weather.DROUGHT -> 25
            Weather.CLOUDY -> 10
            Weather.RAINY -> 5
            Weather.STORMY -> 0
        }
        waterLevel = (waterLevel - waterDecrease).coerceAtLeast(0)
        
        // Nutrients decrease each day
        nutrientLevel = (nutrientLevel - 8).coerceAtLeast(0)
        
        // Check temperature effects
        if (temperature !in optimalTempRange) {
            // Temperature stress
            val tempStress = if (temperature < optimalTempRange.first) {
                (optimalTempRange.first - temperature) * 2
            } else {
                (temperature - optimalTempRange.last) * 2
            }
            health -= tempStress.coerceAtMost(10)
        }
        
        // Calculate growth based on conditions
        calculateGrowth()
        
        // Earn money based on plant stage
        earnMoney()
        
        // Health recovery if conditions are good
        if (waterLevel > 40 && nutrientLevel > 30 && temperature in optimalTempRange && !hasPests) {
            health = (health + 5).coerceAtMost(maxHealth)
        }
        
        // Generate new leaves as the plant grows
        if (growthPoints % 15 == 0 && growthPoints > 0 && leaves.size < getMaxLeaves()) {
            addNewLeaf()
        }
        
        // Plant can die if health reaches zero
        if (health <= 0) {
            health = 0
            isGameOver = true
        }
        
        daysPassed++
    }
    
    private fun getMaxLeaves(): Int {
        return when (plantStage) {
            PlantStage.SEED -> 0
            PlantStage.SPROUT -> 2
            PlantStage.YOUNG -> 5
            PlantStage.MATURE -> 8
            PlantStage.FLOWERING -> 12
        }
    }
    
    private fun addNewLeaf() {
        val newLeaf = Leaf(
            id = if (leaves.isEmpty()) 1 else leaves.maxByOrNull { it.id }!!.id + 1,
            position = when {
                leaves.size < getMaxLeaves() / 3 -> "bottom"
                leaves.size < getMaxLeaves() * 2/3 -> "middle"
                else -> "top"
            }
        )
        
        leaves = leaves + newLeaf
    }
    
    private fun createDiseaseOnLeaf() {
        if (leaves.isEmpty() || currentPests == PestType.NONE) return
        
        // Find a healthy leaf
        val healthyLeaves = leaves.filter { !it.isDiseased }
        if (healthyLeaves.isEmpty()) return
        
        val targetLeaf = healthyLeaves.random()
        val diseaseType = pestToDiseaseMap[currentPests] ?: DiseaseType.LEAF_SPOT
        
        // Create a new list with the infected leaf
        val newLeaves = leaves.toMutableList()
        val index = newLeaves.indexOfFirst { it.id == targetLeaf.id }
        if (index != -1) {
            newLeaves[index] = targetLeaf.copy(
                isDiseased = true,
                diseaseType = diseaseType,
                diseaseProgress = 10
            )
            leaves = newLeaves
        }
    }
    
    private fun processDiseases() {
        if (leaves.none { it.isDiseased }) return
        
        val newLeaves = leaves.toMutableList()
        var spreadDisease = false
        
        // Progress existing diseases
        for (i in newLeaves.indices) {
            if (newLeaves[i].isDiseased) {
                // Progress the disease
                newLeaves[i] = newLeaves[i].copy(
                    diseaseProgress = (newLeaves[i].diseaseProgress + Random.nextInt(5, 15)).coerceAtMost(100)
                )
                
                // Damage plant based on disease progress
                if (newLeaves[i].diseaseProgress >= 50) {
                    health -= (newLeaves[i].diseaseProgress / 20)
                    
                    // Chance to spread to another leaf
                    if (Random.nextInt(100) < 30) {
                        spreadDisease = true
                    }
                }
                
                // If disease reaches 100%, the leaf is destroyed
                if (newLeaves[i].diseaseProgress >= 100) {
                    health -= 10 // Major health penalty
                    newLeaves.removeAt(i)
                    break // Avoid concurrent modification
                }
            }
        }
        
        // Possibly spread disease to another leaf
        if (spreadDisease) {
            val healthyLeaves = newLeaves.filter { !it.isDiseased }
            if (healthyLeaves.isNotEmpty()) {
                val targetLeaf = healthyLeaves.random()
                val sourceLeaf = newLeaves.first { it.isDiseased }
                
                val index = newLeaves.indexOfFirst { it.id == targetLeaf.id }
                if (index != -1) {
                    newLeaves[index] = targetLeaf.copy(
                        isDiseased = true,
                        diseaseType = sourceLeaf.diseaseType,
                        diseaseProgress = 5
                    )
                }
            }
        }
        
        leaves = newLeaves
    }
    
    private fun updateWeather() {
        // Use cached transition probabilities if available
        val weatherChances = weatherTransitionCache.getOrPut(currentWeather) {
            when (currentWeather) {
                Weather.SUNNY -> listOf(60, 20, 15, 3, 2) // Likely to stay sunny
                Weather.CLOUDY -> listOf(25, 40, 25, 7, 3)
                Weather.RAINY -> listOf(15, 30, 40, 10, 5)
                Weather.STORMY -> listOf(5, 20, 30, 40, 5)
                Weather.DROUGHT -> listOf(50, 25, 5, 0, 20) // Drought tends to persist
            }
        }
        
        val randomValue = Random.nextInt(100)
        var cumulativeChance = 0
        
        for (i in Weather.values().indices) {
            cumulativeChance += weatherChances[i]
            if (randomValue < cumulativeChance) {
                currentWeather = Weather.values()[i]
                break
            }
        }
    }
    
    private fun applyWeatherEffects() {
        when (currentWeather) {
            Weather.SUNNY -> {} // Normal growth
            Weather.CLOUDY -> {} // Slightly reduced growth, handled in calculateGrowth
            Weather.RAINY -> {
                waterLevel = (waterLevel + 15).coerceAtMost(maxWaterLevel)
            }
            Weather.STORMY -> {
                health -= 10
                waterLevel = (waterLevel + 25).coerceAtMost(maxWaterLevel)
            }
            Weather.DROUGHT -> {
                health -= 5
            }
        }
    }
    
    private fun generatePests() {
        hasPests = true
        currentPests = PestType.values().let { 
            it[Random.nextInt(it.size - 1)] // Exclude NONE
        }
        pestInfestationDays = 0
    }
    
    private fun applyPestDamage() {
        val damage = when (currentPests) {
            PestType.APHIDS -> 5
            PestType.MITES -> 8
            PestType.FUNGUS -> 12
            PestType.NONE -> 0
        }
        health -= damage
        nutrientLevel = (nutrientLevel - damage / 2).coerceAtLeast(0)
    }
    
    private fun calculateGrowth() {
        // Plant grows if conditions are good
        if (health > 50 && waterLevel > 20 && nutrientLevel > 15) {
            // Base growth amount - explicitly declare as Float
            var growthAmount: Float = (waterLevel / 25 + nutrientLevel / 30).toFloat()
            
            // Weather modifier
            growthAmount *= when (currentWeather) {
                Weather.SUNNY -> 1.2f
                Weather.CLOUDY -> 0.8f
                Weather.RAINY -> 1.0f
                Weather.STORMY -> 0.5f
                Weather.DROUGHT -> 0.3f
            }
            
            // Temperature modifier
            if (temperature in optimalTempRange) {
                growthAmount *= 1.2f
            } else {
                growthAmount *= 0.7f
            }
            
            // Pest modifier
            if (hasPests) {
                growthAmount *= 0.5f
            }
            
            // Disease modifier
            val diseasedLeafCount = leaves.count { it.isDiseased }
            if (diseasedLeafCount > 0) {
                growthAmount *= (1.0f - (diseasedLeafCount.toFloat() / leaves.size.toFloat() * 0.5f))
            }
            
            growthPoints += growthAmount.toInt()
            
            // Check if plant should advance to next stage
            for ((stage, threshold) in growthThresholds) {
                if (plantStage == stage && growthPoints >= threshold) {
                    plantStage = PlantStage.values()[stage.ordinal + 1]
                    break
                }
            }
        }
    }
    
    private fun earnMoney() {
        // Earn money based on plant stage
        val earnings = when (plantStage) {
            PlantStage.SEED -> 0
            PlantStage.SPROUT -> 3
            PlantStage.YOUNG -> 6
            PlantStage.MATURE -> 10
            PlantStage.FLOWERING -> 15
        }
        money += earnings
    }
    
    fun resetGame() {
        if (isProcessing) return
        
        waterLevel = 50
        nutrientLevel = 50
        temperature = 25
        plantStage = PlantStage.SEED
        growthPoints = 0
        daysPassed = 0
        health = 100
        currentWeather = Weather.SUNNY
        hasPests = false
        currentPests = PestType.NONE
        money = 100
        leaves = emptyList()
        pestInfestationDays = 0
        isGameOver = false
    }
} 