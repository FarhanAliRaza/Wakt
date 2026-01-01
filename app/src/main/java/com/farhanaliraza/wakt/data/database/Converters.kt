package com.farhanaliraza.wakt.data.database

import androidx.room.TypeConverter
import com.farhanaliraza.wakt.data.database.entity.BlockType
import com.farhanaliraza.wakt.data.database.entity.ChallengeType
import com.farhanaliraza.wakt.data.database.entity.BrickSessionType
import com.farhanaliraza.wakt.data.database.entity.SessionCompletionStatus

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
    
    @TypeConverter
    fun fromBrickSessionType(value: BrickSessionType): String {
        return value.name
    }
    
    @TypeConverter
    fun toBrickSessionType(value: String): BrickSessionType {
        return BrickSessionType.valueOf(value)
    }
    
    @TypeConverter
    fun fromSessionCompletionStatus(value: SessionCompletionStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toSessionCompletionStatus(value: String): SessionCompletionStatus {
        return SessionCompletionStatus.valueOf(value)
    }
}