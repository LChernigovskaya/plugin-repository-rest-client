package org.jetbrains.intellij.pluginRepository

/**
 * Checks whether the current thread has been interrupted.
 * Clears the *interrupted status* and throws [InterruptedException]
 * if it is the case.
 */
@Throws(InterruptedException::class)
fun checkIfInterrupted() {
    if (Thread.interrupted()) {
        throw InterruptedException()
    }
}