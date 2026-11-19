package com.flowers.app.data

import com.flowers.app.data.remote.GeneratedPlantResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiPlantServiceTest {

    private val gson = Gson()

    @Test
    fun `parse mock Gemini response with markdown fences and structured fields`() {
        val geminiRawResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\n{\n  \"name_uk\": \"Філодендрон Біркін\",\n  \"name_latin\": \"Philodendron Birkin\",\n  \"popular_name\": \"Біркін\",\n  \"difficulty\": \"Легко\",\n  \"light\": \"Яскраве розсіяне світло без прямих сонячних променів\",\n  \"default_check_interval_days\": 7,\n  \"soil_type\": \"Пухкий, повітропроникний субстрат із перлітом і корою\",\n  \"toxic_to_pets\": true,\n  \"care_tip\": \"Поливати після просихання верхнього шару на 2-3 см, протирати листя від пилу.\"\n}\n```"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val root = gson.fromJson(geminiRawResponse, Map::class.java)
        val candidates = root["candidates"] as List<*>
        val firstCandidate = candidates[0] as Map<*, *>
        val content = firstCandidate["content"] as Map<*, *>
        val parts = content["parts"] as List<*>
        val firstPart = parts[0] as Map<*, *>
        val text = firstPart["text"] as String

        val cleanedText = text
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val generated = gson.fromJson(cleanedText, GeneratedPlantResponse::class.java)

        assertNotNull(generated)
        assertEquals("Філодендрон Біркін", generated.nameUk)
        assertEquals("Philodendron Birkin", generated.nameLatin)
        assertEquals("Біркін", generated.popularName)
        assertEquals("Легко", generated.difficulty)
        assertEquals(7, generated.defaultCheckIntervalDays)
        assertTrue(generated.toxicToPets)
        assertTrue(generated.careTip.contains("Поливати"))
    }
}
