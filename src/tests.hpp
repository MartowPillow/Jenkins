#ifndef _TEST_HPP_
#define _TEST_HPP_

#include <iostream>

TEST (TestTest, Un) {
    int a = 5;
    int b = 2+3; 
 
    EXPECT_EQ(a, b);
}

TEST (TestTest, Deux) {
    bool x = true;
 
    EXPECT_TRUE(x);
}

#endif