add_library(dof bno055.c dof.c)
target_include_directories(dof PUBLIC .)
target_link_libraries(dof PUBLIC utils)
