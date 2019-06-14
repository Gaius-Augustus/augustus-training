package webaugustus

class UrlMappings {
    
	static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }
        
        "/"(view:'/index')
        "404"(view: '/404')
        "500"(view:'/error')        
        "/error"(view:'/error')
        "/about"(view:'/about')
        "/accuracy"(view: '/accuracy')
        "/datasets"(view: '/datasets')
        "/help"(view: '/help')
        "/index"(view: '/index')
        "/predictions_for_download"(view: '/predictions_for_download')
        "/predictiontutorial"(view: '/predictiontutorial')
        "/references"(view: '/references')
        "/trainingtutorial"(view: '/trainingtutorial')

        "/prediction"(controller:'Prediction', action:'create' )        
        "/training"(controller:'Training', action:'create' )

	}

}
