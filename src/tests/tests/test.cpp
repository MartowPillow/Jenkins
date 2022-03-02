#include "pch.h"

TEST(TestCaseName, Good) {
  EXPECT_EQ(1, 1);
  EXPECT_TRUE(true);
}

TEST(TestCaseName, Bad) {
  EXPECT_EQ(1, 0);
}