package retrylint.config

class ConfigurationException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
