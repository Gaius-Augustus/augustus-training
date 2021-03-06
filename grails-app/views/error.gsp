<!DOCTYPE html>
<html>
	<head>
		<title><g:if env="development">Grails Runtime Exception</g:if><g:else>Error</g:else></title>
		<meta name="layout" content="main">
		<asset:stylesheet src="errors.css"/>
	</head>
	<body>
        <g:if env="development">
            <p>
                Ooops... this looks ugly! Our apologies! Please send an e-mail to 
                <a href="mailto:${webaugustus.AbstractWebaugustusService.getWebaugustusEmailAddress()}">${webaugustus.AbstractWebaugustusService.getWebaugustusEmailAddress()}</a>. 
                Please tell us date and time and any actions from your side that 
                may have caused this problem. Please also attach a copy of the below 
                shown error log to your e-mail. Thanks for you help!
            </p>
            <g:if test="${Throwable.isInstance(exception)}">
                <g:renderException exception="${exception}" />
            </g:if>
            <g:elseif test="${request.getAttribute('javax.servlet.error.exception')}">
                <g:renderException exception="${request.getAttribute('javax.servlet.error.exception')}" />
            </g:elseif>
            <g:else>
                <ul class="errors">
                    <li>An error has occurred</li>
                    <li>Exception: ${exception}</li>
                    <li>Message: ${message}</li>
                    <li>Path: ${path}</li>
                </ul>
            </g:else>
            
        </g:if>
        <g:else>
            <h2>An error has occurred</h2>
            <p>
                Ooops... this looks ugly! Our apologies! Please send an e-mail to 
                <a href="mailto:${webaugustus.AbstractWebaugustusService.getWebaugustusEmailAddress()}">${webaugustus.AbstractWebaugustusService.getWebaugustusEmailAddress()}</a>. 
                Please tell us date and time and any actions from your side that 
                may have caused this problem.
            </p>
        </g:else>
	</body>
</html>
