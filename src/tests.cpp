#include "../googletest/googletest/include/gtest/gtest.h"
#include "tests.hpp"

int main(int argc,char *argv[]){

    ::testing::InitGoogleTest(&argc,argv);
    return RUN_ALL_TESTS();
}

//g++ ./tests.cpp -Wall -Wextra -o tests

//g++ ./tests.cpp -Wall -Wextra -o tests /usr/src/gtest/src/gtest_main.cc /usr/src/gtest/src/gtest-all.cc -I /usr/include -I /usr/src/gtest -L /usr/local/lib -lpthread
