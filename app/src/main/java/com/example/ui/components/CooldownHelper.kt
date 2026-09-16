package com.example.ui.components

import com.example.data.repository.DjezzyRepository

object CooldownHelper {

    fun getRemaining1Gb(lastActivation: Long): Pair<Boolean, String> {
        if (lastActivation <= 0) return Pair(true, "جاهز للتفعيل")
        val elapsed = System.currentTimeMillis() - lastActivation
        if (elapsed >= DjezzyRepository.COOLDOWN_1GB_MS) {
            return Pair(true, "جاهز للتفعيل")
        }
        val remaining = DjezzyRepository.COOLDOWN_1GB_MS - elapsed
        val hours = remaining / (1000 * 60 * 60)
        val minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60)
        return Pair(false, "متبقي $hours ساعة و $minutes د")
    }

    fun getRemaining2Gb(lastActivation: Long): Pair<Boolean, String> {
        if (lastActivation <= 0) return Pair(true, "جاهز للتفعيل")
        val elapsed = System.currentTimeMillis() - lastActivation
        if (elapsed >= DjezzyRepository.COOLDOWN_2GB_MS) {
            return Pair(true, "جاهز للتفعيل")
        }
        val remaining = DjezzyRepository.COOLDOWN_2GB_MS - elapsed
        val days = remaining / (1000 * 60 * 60 * 24)
        val hours = (remaining % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
        return Pair(false, "متبقي $days يوم و $hours س")
    }

    fun getRemaining3Gb(lastActivation: Long): Pair<Boolean, String> {
        if (lastActivation <= 0) return Pair(true, "جاهز للتفعيل")
        val elapsed = System.currentTimeMillis() - lastActivation
        if (elapsed >= DjezzyRepository.COOLDOWN_3GB_MS) {
            return Pair(true, "جاهز للتفعيل")
        }
        val remaining = DjezzyRepository.COOLDOWN_3GB_MS - elapsed
        val days = remaining / (1000 * 60 * 60 * 24)
        val hours = (remaining % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
        return Pair(false, "متبقي $days يوم و $hours س")
    }
}
