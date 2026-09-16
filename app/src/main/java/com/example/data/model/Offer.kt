package com.example.data.model

data class Offer(
    val code: String,
    val name: String,
    val price: String,
    val category: String,
    val validity: String,
    val dataVolume: String,
    val bonus: String,
    val description: String,
    val isFree: Boolean = false
)

object AvailableOffers {
    val freeOffers = listOf(
        Offer(
            code = "FREE_1GB",
            name = "🎁 1 جيجا (يومي)",
            price = "مجاني (0 دج)",
            category = "المكافآت المجانية",
            validity = "24 ساعة",
            dataVolume = "1 جيجا إنترنت",
            bonus = "مكافأة MGM لدعوة الأصدقاء",
            description = "✅ 1 جيجا إنترنت مجاناً\n✅ صالح لمدة 24 ساعة\n✅ يتجدد كل 24 ساعة تلقائياً\n✅ السعر: 0 دج",
            isFree = true
        ),
        Offer(
            code = "FREE_2GB",
            name = "🎁 2 جيجا (أسبوعي)",
            price = "مجاني (0 دج)",
            category = "المكافآت المجانية",
            validity = "7 أيام",
            dataVolume = "2 جيجا إنترنت",
            bonus = "مكافأة Walk & Win للمشي",
            description = "✅ 2 جيجا إنترنت مجاناً\n✅ صالح لمدة 7 أيام\n✅ يتجدد كل أسبوع\n✅ السعر: 0 دج\n⚠️ اشترك في عرض 100 دج وفقاً لشروط جيزي",
            isFree = true
        ),
        Offer(
            code = "FREE_3GB",
            name = "🎁 3 جيجا (1GB + 2GB)",
            price = "مجاني (0 دج)",
            category = "المكافآت المجانية",
            validity = "7 أيام",
            dataVolume = "3 جيجا إنترنت",
            bonus = "دمج مكافأة 1GB + 2GB",
            description = "✅ 3 جيجا إنترنت مجاناً\n✅ تفعيل متوازي لباقة 1GB و 2GB\n✅ السعر: 0 دج",
            isFree = true
        )
    )

    val paidOffers = listOf(
        // عروض BTL
        Offer(
            code = "1GBFB3DAY",
            name = "📱 70 دج 3GB 3أيام",
            price = "70 دج",
            category = "عروض BTL",
            validity = "3 أيام",
            dataVolume = "3 جيجا إنترنت",
            bonus = "سرعة عالية",
            description = "✅ 3 جيجا إنترنت\n✅ سرعة عالية\n✅ صالح لمدة 3 أيام\n✅ السعر: 70 دج"
        ),
        Offer(
            code = "BTL500MBDAY",
            name = "📱 90 دج - 5GB",
            price = "90 دج",
            category = "عروض BTL",
            validity = "24 ساعة",
            dataVolume = "5 جيجا إنترنت",
            bonus = "وطني",
            description = "✅ 5 جيجا إنترنت\n✅ صالح لمدة 24 ساعة\n✅ السعر: 90 دج"
        ),
        Offer(
            code = "BTL10GB72H",
            name = "📱 190 دج - 10GB",
            price = "190 دج",
            category = "عروض BTL",
            validity = "72 ساعة (3 أيام)",
            dataVolume = "10 جيجا إنترنت",
            bonus = "وطني",
            description = "✅ 10 جيجا إنترنت\n✅ صالح لمدة 72 ساعة\n✅ السعر: 190 دج"
        ),
        Offer(
            code = "2GBMONTH",
            name = "📱 250 دج - 3GB",
            price = "250 دج",
            category = "عروض BTL",
            validity = "30 يوم",
            dataVolume = "3 جيجا إنترنت (3072MB)",
            bonus = "وطني - غير قابل للمشاركة",
            description = "✅ 3 جيجا إنترنت (3072MB)\n✅ صالح لمدة 30 يوم\n✅ السعر: 250 دج\n✅ غير قابلة للمشاركة"
        ),
        Offer(
            code = "BTLDATA2WEEKS",
            name = "📱 400 دج - 4GB",
            price = "400 دج",
            category = "عروض BTL",
            validity = "15 يوم",
            dataVolume = "4 جيجا إنترنت",
            bonus = "وطني",
            description = "✅ 4 جيجا إنترنت\n✅ صالح لمدة 15 يوم\n✅ السعر: 400 دج"
        ),

        // عروض SPEED
        Offer(
            code = "DOVINTSPEEDDAY100MoPRE",
            name = "🚀 30 دج (300MB)",
            price = "30 دج",
            category = "عروض SPEED",
            validity = "24 ساعة",
            dataVolume = "300 ميجا إنترنت",
            bonus = "سرعة عالية",
            description = "✅ 300 ميجا إنترنت\n✅ سرعة عالية\n✅ صالح لمدة 24 ساعة\n✅ السعر: 30 دج"
        ),
        Offer(
            code = "DOVINTSPEEDDAY250MoPRE",
            name = "🚀 50 دج (600MB)",
            price = "50 دج",
            category = "عروض SPEED",
            validity = "24 ساعة",
            dataVolume = "600 ميجا إنترنت",
            bonus = "سرعة عالية",
            description = "✅ 600 ميجا إنترنت\n✅ سرعة عالية\n✅ صالح لمدة 24 ساعة\n✅ السعر: 50 دج"
        ),
        Offer(
            code = "DOVINTSPEEDDAY1GoPRE",
            name = "🚀 100 دج (2GB)",
            price = "100 دج",
            category = "عروض SPEED",
            validity = "24 ساعة",
            dataVolume = "2 جيجا إنترنت",
            bonus = "سرعة عالية",
            description = "✅ 2 جيجا إنترنت\n✅ سرعة عالية\n✅ صالح لمدة 24 ساعة\n✅ السعر: 100 دج"
        ),
        Offer(
            code = "DOVINTSPEEDWEEK2GoPRE",
            name = "🚀 150 دج (4GB)",
            price = "150 دج",
            category = "عروض SPEED",
            validity = "7 أيام",
            dataVolume = "4 جيجا إنترنت",
            bonus = "سرعة عالية",
            description = "✅ 4 جيجا إنترنت\n✅ سرعة عالية\n✅ صالح لمدة 7 أيام\n✅ السعر: 150 دج"
        ),
        Offer(
            code = "DOVINTSPEEDWEEK3GoPRE",
            name = "🚀 300 دج (10GB)",
            price = "300 دج",
            category = "عروض SPEED",
            validity = "7 أيام",
            dataVolume = "10 جيجا إنترنت",
            bonus = "سرعة عالية",
            description = "✅ 10 جيجا إنترنت\n✅ سرعة عالية\n✅ صالح لمدة 7 أيام\n✅ السعر: 300 دج"
        ),
        Offer(
            code = "DOVINTSPEEDMONTH6GoPRE",
            name = "🚀 500 دج (12GB)",
            price = "500 دج",
            category = "عروض SPEED",
            validity = "30 يوم",
            dataVolume = "12 جيجا إنترنت",
            bonus = "سرعة عالية",
            description = "✅ 12 جيجا إنترنت\n✅ سرعة عالية\n✅ صالح لمدة 30 يوم\n✅ السعر: 500 دج"
        ),
        Offer(
            code = "DOVINTSPEEDMONTH15GoPRE",
            name = "🚀 1000 دج (30GB)",
            price = "1000 دج",
            category = "عروض SPEED",
            validity = "30 يوم",
            dataVolume = "30 جيجا إنترنت",
            bonus = "سرعة عالية",
            description = "✅ 30 جيجا إنترنت\n✅ سرعة عالية\n✅ صالح لمدة 30 يوم\n✅ السعر: 1000 دج"
        ),
        Offer(
            code = "DOVINTSPEEDMONTH30GoPRE",
            name = "🚀 1500 دج (60GB)",
            price = "1500 دج",
            category = "عروض SPEED",
            validity = "30 يوم",
            dataVolume = "60 جيجا إنترنت",
            bonus = "سرعة عالية",
            description = "✅ 60 جيجا إنترنت\n✅ سرعة عالية\n✅ صالح لمدة 30 يوم\n✅ السعر: 1500 دج"
        ),

        // عروض MIXTE
        Offer(
            code = "MIXTEONNET50",
            name = "🔄 MIXTE ON NET 50",
            price = "50 دج",
            category = "عروض MIXTE",
            validity = "24 ساعة",
            dataVolume = "60 دقيقة مكالمات",
            bonus = "نحو جيزي",
            description = "✅ 60 دقيقة مكالمات نحو جيزي\n✅ صالح لمدة 24 ساعة\n✅ السعر: 50 دج"
        ),
        Offer(
            code = "MIXTEPRE100",
            name = "🔄 MIXTE 100",
            price = "100 دج",
            category = "عروض MIXTE",
            validity = "24 ساعة",
            dataVolume = "1 جيجا إنترنت",
            bonus = "300 دج رصيد",
            description = "✅ 1 جيجا إنترنت\n✅ 300 دج رصيد مكالمات\n✅ صالح لمدة 24 ساعة\n✅ السعر: 100 دج"
        ),
        Offer(
            code = "MIXTEPRE1000",
            name = "🔄 MIXTE 1000",
            price = "1000 دج",
            category = "عروض MIXTE",
            validity = "30 يوم",
            dataVolume = "15 جيجا إنترنت",
            bonus = "2000 دج رصيد",
            description = "✅ 15 جيجا إنترنت\n✅ 2000 دج رصيد مكالمات\n✅ صالح لمدة 30 يوم\n✅ السعر: 1000 دج"
        ),
        Offer(
            code = "MIXTEPRE1500",
            name = "🔄 MIXTE 1500",
            price = "1500 دج",
            category = "عروض MIXTE",
            validity = "30 يوم",
            dataVolume = "40 جيجا إنترنت",
            bonus = "3000 دج رصيد",
            description = "✅ 40 جيجا إنترنت\n✅ 3000 دج رصيد مكالمات\n✅ صالح لمدة 30 يوم\n✅ السعر: 1500 دج"
        ),

        // عروض FAMILY
        Offer(
            code = "FAMILY2000",
            name = "👨‍👩‍👧‍👦 FAMILY 2000",
            price = "2000 دج",
            category = "عروض FAMILY",
            validity = "30 يوم",
            dataVolume = "20 جيجا إنترنت",
            bonus = "2000 دج رصيد",
            description = "✅ 20 جيجا إنترنت\n✅ 2000 دج رصيد\n✅ صالح لمدة 30 يوم\n✅ السعر: 2000 دج"
        ),
        Offer(
            code = "FAMILY3000",
            name = "👨‍👩‍👧‍👦 FAMILY 3000",
            price = "3000 دج",
            category = "عروض FAMILY",
            validity = "30 يوم",
            dataVolume = "50 جيجا إنترنت",
            bonus = "4000 دج رصيد",
            description = "✅ 50 جيجا إنترنت\n✅ 4000 دج رصيد\n✅ صالح لمدة 30 يوم\n✅ السعر: 3000 دج"
        ),
        Offer(
            code = "FAMILY4000",
            name = "👨‍👩‍👧‍👦 FAMILY 4000",
            price = "4000 دج",
            category = "عروض FAMILY",
            validity = "30 يوم",
            dataVolume = "100 جيجا إنترنت",
            bonus = "6000 دج رصيد",
            description = "✅ 100 جيجا إنترنت\n✅ 6000 دج رصيد\n✅ صالح لمدة 30 يوم\n✅ السعر: 4000 دج"
        ),

        // عروض IZZY
        Offer(
            code = "OFFREJEUNE1200",
            name = "🎁 IZZY 120 ألف",
            price = "120 دج",
            category = "عروض IZZY",
            validity = "30 يوم",
            dataVolume = "10 جيجا إنترنت",
            bonus = "يوتيوب غير محدود",
            description = "✅ 10 جيجا إنترنت\n✅ يوتيوب غير محدود\n✅ صالح لمدة 30 يوم\n✅ السعر: 120 دج"
        )
    )

    val allOffers: List<Offer> = freeOffers + paidOffers
}
