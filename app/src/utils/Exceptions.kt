package utils

class InvalidMimeTypeException(message: String = "File doesn't have a valid mime type") : Exception(message)
class FileCreationException(message: String) : Exception(message)