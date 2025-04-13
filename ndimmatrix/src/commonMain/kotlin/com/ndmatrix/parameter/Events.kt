package com.ndmatrix.parameter

sealed interface Message {
    interface Event : Message
    interface Intent : Message
}