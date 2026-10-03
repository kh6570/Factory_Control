// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    /** Host did not answer or refused the connection. */
    data object Unreachable : AppError

    /** Wrong or missing username / password. */
    data object Unauthorized : AppError

    /** Device answered, but no working stream was found. */
    data object NoStreamFound : AppError

    data object Timeout : AppError

    data object Offline : AppError

    data object NotFound : AppError

    data class Unknown(val message: String?) : AppError
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> = also {
    if (it is AppResult.Success) action(it.value)
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> = also {
    if (it is AppResult.Failure) action(it.error)
}
