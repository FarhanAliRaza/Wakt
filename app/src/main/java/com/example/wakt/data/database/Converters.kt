package com.example.wakt.data.database

import androidx.room.TypeConverter
import com.example.wakt.data.database.entity.BlockType
import com.example.wakt.data.database.entity.ChallengeType

class Converters {
    
    @TypeConverter
    fun fromBlockType(value: BlockType): String {
        return value.name
    }
    
    @TypeConverter
    fun toBlockType(value: String): BlockType {
        return BlockType.valueOf(value)
    }
    
    @TypeConverter
    fun fromChallengeType(value: ChallengeType): String {
        return value.name
    }
    
    @TypeConverter
    fun toChallengeType(value: String): ChallengeType {
        return ChallengeType.valueOf(value)
    }
}