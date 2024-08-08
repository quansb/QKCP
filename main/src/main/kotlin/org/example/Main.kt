package org.example

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        Test().test2("123")
    }

}

class Test {

    @DebugLog
    fun test() {
        work()
    }

    @DebugLog
    fun test2(name: String) {

    }

    private fun work() {
        Thread.sleep(3000)
    }

}


