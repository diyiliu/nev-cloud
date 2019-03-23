package com.tiza.api.support.facade;

import com.tiza.api.support.facade.dto.Instruction;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Description: InstructionJpa
 * Author: DIYILIU
 * Update: 2019-03-22 17:49
 */
public interface InstructionJpa extends JpaRepository<Instruction, Long> {

}
