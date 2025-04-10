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

// Cache for weather probabilities to avoid recreating them
private val weatherTransitionCache = mutableMapOf<Weather, List<Int>>()

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
        if (isProcessing) return
        if (waterLevel < maxWaterLevel) {
            waterLevel = (waterLevel + 15).coerceAtMost(maxWaterLevel)
            money -= 2 // Water costs money
            money = money.coerceAtLeast(0)
        }
    }
    
    fun addNutrients() {
        if (isProcessing) return
        if (nutrientLevel < maxNutrientLevel) {
            nutrientLevel = (nutrientLevel + 25).coerceAtMost(maxNutrientLevel)
            money -= 5 // Nutrients cost money
            money = money.coerceAtLeast(0)
        }
    }
    
    fun treatPests() {
        if (isProcessing) return
        if (hasPests) {
            hasPests = false
            currentPests = PestType.NONE
            health -= 5 // Pesticides slightly harm the plant too
            money -= 10 // Pest treatment costs money
            money = money.coerceAtLeast(0)
        }
    }
    
    fun adjustTemperature(increase: Boolean) {
        if (isProcessing) return
        temperature += if (increase) 2 else -2
        money -= 3 // Temperature control costs money
        money = money.coerceAtLeast(0)
    }
    
    fun advanceDay() {
        if (isProcessing) return
        
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
        }
        
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
        
        // Plant can die if health reaches zero
        if (health <= 0) {
            resetGame()
        }
        
        daysPassed++
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
    }
} 