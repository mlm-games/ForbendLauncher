package org.mlm.forbendlauncher.recommendations

import org.mlm.forbendlauncher.service.BaseRecommendationsService

class RecommendationsService : BaseRecommendationsService(
    false,
    NotificationsServiceV4::class.java,
    GservicesRankerParameters.Factory()
)

