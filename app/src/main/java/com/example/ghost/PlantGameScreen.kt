package com.example.ghost

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ghost.ui.theme.GhostTheme

@Composable
fun PlantGameScreen(
    plantGame: PlantGame,
    modifier: Modifier = Modifier
) {
    // Use LazyColumn instead of verticalScroll for better performance
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            GameHeader(daysPassed = plantGame.daysPassed, money = plantGame.money)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Memoize weather display to avoid unnecessary recompositions
            val weatherInfo = remember(plantGame.currentWeather) {
                getWeatherInfo(plantGame.currentWeather)
            }
            WeatherDisplay(weatherInfo = weatherInfo)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Health meter
                MeterDisplay(
                    value = plantGame.health,
                    label = "Health",
                    color = Color(0xFFF44336),
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                
                // Temperature display
                TemperatureDisplay(
                    temperature = plantGame.temperature,
                    onIncreaseTemp = { plantGame.adjustTemperature(true) },
                    onDecreaseTemp = { plantGame.adjustTemperature(false) },
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Memoize plant display
            key(plantGame.plantStage, plantGame.hasPests, plantGame.currentPests) {
                PlantDisplay(
                    plantStage = plantGame.plantStage,
                    hasPests = plantGame.hasPests,
                    pestType = plantGame.currentPests
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Water meter
                MeterDisplay(
                    value = plantGame.waterLevel,
                    label = "Water",
                    color = Color(0xFF03A9F4),
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                
                // Nutrient meter
                MeterDisplay(
                    value = plantGame.nutrientLevel,
                    label = "Nutrients",
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            ActionButtonsSection(
                plantGame = plantGame,
                isProcessing = plantGame.isProcessing
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LoadingIndicator(visible = plantGame.isProcessing)
            
            Button(
                onClick = { plantGame.resetGame() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF795548)),
                enabled = !plantGame.isProcessing
            ) {
                Text("Reset Game", color = Color.White)
            }
        }
    }
}

@Composable
fun LoadingIndicator(visible: Boolean) {
    if (visible) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Processing...", color = Color.Gray)
        }
    }
}

@Composable
fun ActionButtonsSection(
    plantGame: PlantGame,
    isProcessing: Boolean
) {
    // Action buttons row 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionButton(
            text = "Water Plant\n($2)",
            icon = R.drawable.seed,
            onClick = { plantGame.waterPlant() },
            color = Color(0xFF03A9F4),
            enabled = plantGame.money >= 2 && !isProcessing
        )
        
        ActionButton(
            text = "Add Nutrients\n($5)",
            icon = R.drawable.seed,
            onClick = { plantGame.addNutrients() },
            color = Color(0xFF4CAF50),
            enabled = plantGame.money >= 5 && !isProcessing
        )
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // Action buttons row 2
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Get pest icon once to avoid lookups in conditional
        val pestIcon = if (plantGame.hasPests) {
            when (plantGame.currentPests) {
                PestType.APHIDS -> R.drawable.pest_aphids
                PestType.MITES -> R.drawable.pest_mites
                PestType.FUNGUS -> R.drawable.pest_fungus
                else -> R.drawable.seed
            }
        } else R.drawable.seed
        
        ActionButton(
            text = "Treat Pests\n($10)",
            icon = pestIcon,
            onClick = { plantGame.treatPests() },
            color = Color(0xFFFF9800),
            enabled = plantGame.hasPests && plantGame.money >= 10 && !isProcessing
        )
        
        ActionButton(
            text = "Next Day",
            icon = R.drawable.seed,
            onClick = { plantGame.advanceDay() },
            color = Color(0xFF9C27B0),
            enabled = !isProcessing
        )
    }
}

// Weather information data class to reduce calculations
data class WeatherInfo(
    val icon: Int,
    val label: String,
    val color: Color
)

// Calculate weather info outside of composition
fun getWeatherInfo(weather: Weather): WeatherInfo {
    return when (weather) {
        Weather.SUNNY -> WeatherInfo(
            R.drawable.weather_sunny,
            "Sunny",
            Color(0xFFFFC107)
        )
        Weather.CLOUDY -> WeatherInfo(
            R.drawable.weather_cloudy,
            "Cloudy",
            Color(0xFF90A4AE)
        )
        Weather.RAINY -> WeatherInfo(
            R.drawable.weather_rainy,
            "Rainy",
            Color(0xFF2196F3)
        )
        Weather.STORMY -> WeatherInfo(
            R.drawable.weather_stormy,
            "Stormy",
            Color(0xFF455A64)
        )
        Weather.DROUGHT -> WeatherInfo(
            R.drawable.weather_drought,
            "Drought",
            Color(0xFFFF5722)
        )
    }
}

@Composable
fun GameHeader(daysPassed: Int, money: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Plant Growing Game",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Day: $daysPassed",
                fontSize = 18.sp,
                color = Color(0xFF5D4037)
            )
            
            Text(
                text = "Money: $$money",
                fontSize = 18.sp,
                color = Color(0xFF5D4037),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WeatherDisplay(weatherInfo: WeatherInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(weatherInfo.color.copy(alpha = 0.2f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = weatherInfo.icon),
            contentDescription = "Weather: ${weatherInfo.label}",
            modifier = Modifier.size(40.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "Weather: ${weatherInfo.label}",
            fontSize = 16.sp,
            color = weatherInfo.color
        )
    }
}

@Composable
fun MeterDisplay(
    value: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = "$label: $value%",
            fontSize = 16.sp,
            color = color
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = value / 100f,
            modifier = Modifier
                .height(12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun TemperatureDisplay(
    temperature: Int,
    onIncreaseTemp: () -> Unit,
    onDecreaseTemp: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculate color once based on temperature range
    val tempColor = remember(temperature) {
        when {
            temperature < 15 -> Color(0xFF2196F3) // Cold (blue)
            temperature > 30 -> Color(0xFFFF5722) // Hot (red)
            else -> Color(0xFF4CAF50) // Good range (green)
        }
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = "Temp: ${temperature}°C",
            fontSize = 16.sp,
            color = tempColor
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDecreaseTemp,
                modifier = Modifier.size(32.dp)
            ) {
                Text("-", fontWeight = FontWeight.Bold)
            }
            
            LinearProgressIndicator(
                progress = (temperature - 10) / 30f, // Range approx 10-40
                modifier = Modifier
                    .height(12.dp)
                    .width(60.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = tempColor,
                trackColor = tempColor.copy(alpha = 0.2f)
            )
            
            IconButton(
                onClick = onIncreaseTemp,
                modifier = Modifier.size(32.dp)
            ) {
                Text("+", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PlantDisplay(
    plantStage: PlantStage,
    hasPests: Boolean,
    pestType: PestType
) {
    // Calculate image resource once
    val imageRes = remember(plantStage) {
        when (plantStage) {
            PlantStage.SEED -> R.drawable.seed
            PlantStage.SPROUT -> R.drawable.sprout
            PlantStage.YOUNG -> R.drawable.young_plant
            PlantStage.MATURE -> R.drawable.mature_plant
            PlantStage.FLOWERING -> R.drawable.flowering_plant
        }
    }
    
    // Calculate pest icon once
    val pestIcon = remember(hasPests, pestType) {
        if (hasPests) {
            when (pestType) {
                PestType.APHIDS -> R.drawable.pest_aphids
                PestType.MITES -> R.drawable.pest_mites
                PestType.FUNGUS -> R.drawable.pest_fungus
                else -> null
            }
        } else null
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE0F2F1))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "Plant at ${plantStage.name} stage",
                modifier = Modifier.size(160.dp)
            )
            
            // Show pest overlay if there are pests
            pestIcon?.let {
                Image(
                    painter = painterResource(id = it),
                    contentDescription = "Pest: ${pestType.name}",
                    modifier = Modifier
                        .size(80.dp)
                        .align(Alignment.BottomEnd)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Growth Stage: ${plantStage.name}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF33691E)
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: Int,
    onClick: () -> Unit,
    color: Color,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = color.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .width(150.dp)
            .height(80.dp),
        enabled = enabled
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = text,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PlantGameScreenPreview() {
    GhostTheme {
        PlantGameScreen(PlantGame())
    }
} 