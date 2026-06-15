package com.bird.fiber.domain.usecase

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateFileNameUseCase @Inject constructor() {

    companion object {
        private val formatter = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yy-MM-dd_HH-mm-ss", Locale.getDefault())
            }
        }
    }

    operator fun invoke(): String {
        return formatter.get()!!.format(Date())
    }
}
