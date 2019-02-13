package utils

class IPFSBinaryException(message: String): Exception(message)

class InvalidMimeTypeException(message: String = "File doesn't have a valid mime type") : Exception(message)
class FileCreationException(message: String) : Exception(message)